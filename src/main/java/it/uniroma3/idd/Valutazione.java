package it.uniroma3.idd;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * TERZO PROGRAMMA: valuta la qualità delle risposte del motore di ricerca.
 *
 * Uso: mvn compile exec:java -Dexec.mainClass=it.uniroma3.idd.Valutazione
 *          [-Dexec.args="<collezione> <cartella valutazione>"]
 * Senza argomenti usa collezione_appunti e valutazione.
 *
 * Legge dalla cartella di valutazione:
 *  - query.tsv : id, query (stessa sintassi di Ricerca), bisogno informativo
 *  - qrels.tsv : per ogni query, i file rilevanti (giudizi di rilevanza)
 *
 * Indicizza la collezione due volte, in memoria:
 *  - SISTEMA:  gli analyzer del progetto (ItalianAnalyzer sul contenuto)
 *  - BASELINE: StandardAnalyzer sul contenuto (nessuno stemming, nessuna stopword)
 * e per ogni query calcola le metriche viste a lezione:
 *  - precision e recall sull'insieme dei documenti restituiti, e F1;
 *  - P@10 (precision sui primi 10 risultati);
 *  - average precision (AP); la media delle AP sulle query è la MAP.
 * Alla fine elenca, per il sistema, i documenti rilevanti NON trovati (falsi negativi),
 * utili per capire i problemi di recall. I risultati sono salvati in risultati.txt.
 */
public class Valutazione {

    private static final int K = 10;
    private static final StringBuilder out = new StringBuilder();

    public static void main(String[] args) throws IOException, ParseException {
        Path collezione = Paths.get(args.length > 0 ? args[0] : "collezione_appunti");
        Path cartellaVal = Paths.get(args.length > 1 ? args[1] : "valutazione");

        Map<String, String> query = new LinkedHashMap<>();
        for (String r : righe(cartellaVal.resolve("query.tsv"))) {
            String[] c = r.split("\t");
            query.put(c[0], c[1]);
        }
        Map<String, Set<String>> rilevanti = new LinkedHashMap<>();
        for (String r : righe(cartellaVal.resolve("qrels.tsv"))) {
            String[] c = r.split("\t");
            rilevanti.computeIfAbsent(c[0], k -> new HashSet<>()).add(c[1]);
        }

        List<Path> files = Indicizzatore.trovaFileTxt(collezione);
        stampa("Collezione: %s (%d file), %d query%n", collezione, files.size(), query.size());

        Analyzer baseline = new PerFieldAnalyzerWrapper(new StandardAnalyzer(),
                Map.of(Analizzatori.CAMPO_NOME, Analizzatori.analyzerNome(),
                       Analizzatori.CAMPO_CONTENUTO, new StandardAnalyzer()));

        Map<String, Set<String>> mancatiSistema =
                valuta("SISTEMA (ItalianAnalyzer)", Analizzatori.perCampo(), files, query, rilevanti);
        valuta("BASELINE (StandardAnalyzer)", baseline, files, query, rilevanti);

        stampa("%nDocumenti rilevanti NON restituiti dal SISTEMA (falsi negativi):%n");
        for (Map.Entry<String, Set<String>> e : mancatiSistema.entrySet()) {
            if (!e.getValue().isEmpty()) {
                stampa("  %s: %s%n", e.getKey(), String.join(", ", new TreeSet<>(e.getValue())));
            }
        }
        Files.writeString(cartellaVal.resolve("risultati.txt"), out.toString(), StandardCharsets.UTF_8);
        System.out.println("\nRisultati salvati in " + cartellaVal.resolve("risultati.txt"));
    }

    /** Indicizza la collezione con l'analyzer dato, esegue tutte le query e stampa le metriche. */
    private static Map<String, Set<String>> valuta(String nome, Analyzer analyzer, List<Path> files,
            Map<String, String> query, Map<String, Set<String>> rilevanti) throws IOException, ParseException {
        Map<String, Set<String>> mancati = new LinkedHashMap<>();
        try (Directory dir = new ByteBuffersDirectory()) {
            long inizio = System.nanoTime();
            Indicizzatore.indicizza(files, dir, analyzer);
            long ms = (System.nanoTime() - inizio) / 1_000_000;

            stampa("%n=== %s: indicizzazione di %d file in %d ms%n", nome, files.size(), ms);
            stampa("%-4s %5s %5s %4s %7s %7s %7s %7s %7s%n",
                    "id", "rel", "rest", "tp", "P", "R", "F1", "P@10", "AP");

            double sommaP = 0, sommaR = 0, sommaF1 = 0, sommaPK = 0, sommaAP = 0;
            try (DirectoryReader reader = DirectoryReader.open(dir)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                for (Map.Entry<String, String> q : query.entrySet()) {
                    Set<String> rel = rilevanti.getOrDefault(q.getKey(), Set.of());
                    Query lq = Ricerca.costruisciQuery(q.getValue(), analyzer);

                    // Chiedo TUTTI i documenti che soddisfano la query, in ordine di punteggio
                    TopDocs td = searcher.search(lq, Math.max(1, reader.numDocs()));
                    List<String> ranking = new ArrayList<>();
                    for (ScoreDoc sd : td.scoreDocs) {
                        String percorso = searcher.storedFields().document(sd.doc).get(Analizzatori.CAMPO_PERCORSO);
                        ranking.add(Paths.get(percorso).getFileName().toString());
                    }

                    int tp = 0, tpK = 0;
                    double sommaPrec = 0;  // somma delle P@k nelle posizioni dei rilevanti
                    for (int i = 0; i < ranking.size(); i++) {
                        if (rel.contains(ranking.get(i))) {
                            tp++;
                            sommaPrec += (double) tp / (i + 1);
                            if (i < K) tpK++;
                        }
                    }
                    double p = ranking.isEmpty() ? 0 : (double) tp / ranking.size();
                    double r = rel.isEmpty() ? 0 : (double) tp / rel.size();
                    double f1 = (p + r) == 0 ? 0 : 2 * p * r / (p + r);
                    double pK = (double) tpK / K;
                    // I rilevanti mai restituiti contano con precision 0: si divide per |rilevanti|
                    double ap = rel.isEmpty() ? 0 : sommaPrec / rel.size();

                    stampa("%-4s %5d %5d %4d %7.3f %7.3f %7.3f %7.3f %7.3f%n",
                            q.getKey(), rel.size(), ranking.size(), tp, p, r, f1, pK, ap);
                    sommaP += p; sommaR += r; sommaF1 += f1; sommaPK += pK; sommaAP += ap;

                    Set<String> fn = new HashSet<>(rel);
                    fn.removeAll(ranking);
                    mancati.put(q.getKey(), fn);
                }
            }
            int n = query.size();
            stampa("%-4s %5s %5s %4s %7.3f %7.3f %7.3f %7.3f %7.3f   <- medie (la media delle AP e' la MAP)%n",
                    "media", "", "", "", sommaP / n, sommaR / n, sommaF1 / n, sommaPK / n, sommaAP / n);
        }
        return mancati;
    }

    /** Righe di un file .tsv, senza l'intestazione e senza righe vuote. */
    private static List<String> righe(Path file) throws IOException {
        List<String> tutte = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> res = new ArrayList<>();
        for (String r : tutte.subList(1, tutte.size())) {
            if (!r.isBlank()) res.add(r);
        }
        return res;
    }

    private static void stampa(String formato, Object... valori) {
        String s = String.format(Locale.ITALY, formato, valori);
        System.out.print(s);
        out.append(s);
    }
}
