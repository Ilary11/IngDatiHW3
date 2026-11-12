package it.uniroma3.idd;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * TERZO PROGRAMMA: valuta la qualità delle risposte del motore di ricerca.
 *
 * Uso: mvn compile exec:java -Dexec.mainClass=it.uniroma3.idd.Valutazione
 *          [-Dexec.args="<collezione> <cartella valutazione>"]
 * Default: collezione_wikipedia e valutazione.
 *
 * Legge dalla cartella di valutazione:
 *  - query.tsv : id, query (stessa sintassi di Ricerca), bisogno informativo;
 *  - qrels.tsv : query, file, giudizio (1 = rilevante, 0 = non rilevante), origine del giudizio
 *                ("pool" = documento del pool, "campione" = documento estratto a caso fuori dal pool);
 *  - campione.tsv: per ogni documento del campione, quanti documenti c'erano fuori dal pool
 *                nella categoria da cui e' stato estratto (serve per la stima della recall).
 *
 * I giudizi esistono solo per una piccola parte delle coppie query-documento: come in TREC, un
 * documento non giudicato viene considerato non rilevante. Per ogni configurazione
 * (Configurazioni.tutte()) e per ogni query calcola le metriche viste a lezione:
 *  - P@5 e P@10: precision sui primi 5 e 10 risultati (esatte: i primi 10 sono tutti giudicati);
 *  - R: recall rispetto ai rilevanti NOTI (pool + campione): sovrastima la recall vera,
 *       perche' i rilevanti mai giudicati non si conoscono;
 *  - R stim.: recall stimata. Il campione dice quale frazione dei documenti fuori dal pool e'
 *       rilevante; la si estende a tutti i documenti della categoria fuori dal pool, e si stima
 *       anche quanti di quei rilevanti il sistema trova (stessa frazione osservata nel campione);
 *  - R@10 stim.: frazione dei rilevanti stimati che compare nei primi 10 risultati;
 *  - AP: average precision, dividendo per il numero di rilevanti noti; la media e' la MAP.
 * I risultati sono salvati in risultati.txt.
 */
public class Valutazione {

    private static final StringBuilder out = new StringBuilder();
    /** origine.get(query).get(file) = "pool" oppure "campione" */
    private static final Map<String, Map<String, String>> origine = new HashMap<>();
    /** per ogni query: [documenti nel campione, documenti della categoria fuori dal pool] */
    private static final Map<String, int[]> campione = new HashMap<>();

    public static void main(String[] args) throws IOException, ParseException {
        Path collezione = Paths.get(args.length > 0 ? args[0] : "collezione_wikipedia");
        Path cartellaVal = Paths.get(args.length > 1 ? args[1] : "valutazione");

        Map<String, String> query = leggiQuery(cartellaVal.resolve("query.tsv"));
        Map<String, Map<String, Boolean>> giudizi = new HashMap<>();
        for (String r : righe(cartellaVal.resolve("qrels.tsv"))) {
            String[] c = r.split("\t");
            giudizi.computeIfAbsent(c[0], x -> new HashMap<>()).put(c[1], c[2].trim().equals("1"));
            origine.computeIfAbsent(c[0], x -> new HashMap<>()).put(c[1], c.length > 3 ? c[3].trim() : "pool");
        }
        Path fileCampione = cartellaVal.resolve("campione.tsv");
        if (Files.exists(fileCampione)) {
            for (String r : righe(fileCampione)) {
                String[] c = r.split("\t");
                int[] v = campione.computeIfAbsent(c[0], x -> new int[2]);
                v[0]++;
                v[1] = Integer.parseInt(c[2].trim());
            }
        }

        List<Path> files = Indicizzatore.trovaFileTxt(collezione);
        int nGiudizi = giudizi.values().stream().mapToInt(Map::size).sum();
        stampa("Collezione: %s (%d file), %d query, %d giudizi di rilevanza (%.1f%% delle %d coppie)%n",
                collezione, files.size(), query.size(), nGiudizi,
                100.0 * nGiudizi / ((double) files.size() * query.size()), files.size() * query.size());

        Map<String, Set<String>> mancatiSistema = null;
        for (Configurazioni.Configurazione conf : Configurazioni.tutte()) {
            Map<String, Set<String>> mancati = valuta(conf, files, query, giudizi);
            if (mancatiSistema == null) mancatiSistema = mancati;
        }

        stampa("%nRilevanti noti NON restituiti dal SISTEMA (falsi negativi):%n");
        for (Map.Entry<String, Set<String>> e : mancatiSistema.entrySet()) {
            if (!e.getValue().isEmpty()) {
                stampa("  %s: %s%n", e.getKey(), String.join(", ", new TreeSet<>(e.getValue())));
            }
        }
        Files.write(cartellaVal.resolve("risultati.txt"), out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("\nRisultati salvati in " + cartellaVal.resolve("risultati.txt"));
    }

    private static Map<String, Set<String>> valuta(Configurazioni.Configurazione conf, List<Path> files,
            Map<String, String> query, Map<String, Map<String, Boolean>> giudizi) throws IOException, ParseException {
        Map<String, Set<String>> mancati = new LinkedHashMap<>();
        try (Directory dir = new ByteBuffersDirectory()) {
            long inizio = System.nanoTime();
            Indicizzatore.indicizza(files, dir, conf.analyzer);
            long ms = (System.nanoTime() - inizio) / 1_000_000;

            stampa("%n=== %s: indicizzazione di %d file in %d ms%n", conf.nome, files.size(), ms);
            stampa("%-5s %5s %6s %6s %6s %6s %7s %6s %10s %8s%n", "id", "rel", "stima", "rest", "P@5", "P@10", "AP", "R", "R@10 stim.", "R stim.");

            double sP5 = 0, sP10 = 0, sR = 0, sRs = 0, sR10 = 0, sAP = 0;
            try (DirectoryReader reader = DirectoryReader.open(dir)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                for (Map.Entry<String, String> q : query.entrySet()) {
                    Map<String, Boolean> g = giudizi.getOrDefault(q.getKey(), Map.of());
                    Set<String> rel = new HashSet<>();
                    g.forEach((f, r) -> { if (r) rel.add(f); });

                    // Tutti i documenti che soddisfano la query, in ordine di punteggio
                    ScoreDoc[] hits = searcher.search(conf.query(q.getValue()), Math.max(1, reader.numDocs())).scoreDocs;
                    List<String> ranking = new ArrayList<>();
                    for (ScoreDoc sd : hits) ranking.add(nomeFile(searcher, sd.doc));

                    int tp = 0, tp5 = 0, tp10 = 0, giud10 = 0;
                    double sommaPrec = 0;
                    for (int i = 0; i < ranking.size(); i++) {
                        String f = ranking.get(i);
                        if (i < 10 && g.containsKey(f)) giud10++;
                        if (rel.contains(f)) {
                            tp++;
                            sommaPrec += (double) tp / (i + 1);
                            if (i < 5) tp5++;
                            if (i < 10) tp10++;
                        }
                    }
                    double p5 = tp5 / 5.0, p10 = tp10 / 10.0;
                    double r = rel.isEmpty() ? 0 : (double) tp / rel.size();
                    double ap = rel.isEmpty() ? 0 : sommaPrec / rel.size();
                    double rStim = recallStimata(q.getKey(), rel, new HashSet<>(ranking));
                    double stima = rilevantiStimati(q.getKey(), rel);
                    double r10 = stima == 0 ? 0 : tp10 / stima;
                    if (giud10 < Math.min(10, ranking.size())) {
                        stampa("  attenzione: %s ha %d documenti non giudicati nei primi 10%n", q.getKey(), Math.min(10, ranking.size()) - giud10);
                    }

                    stampa("%-5s %5d %6.1f %6d %6.3f %6.3f %7.3f %6.3f %10.3f %8.3f%n", q.getKey(), rel.size(), stima,
                            ranking.size(), p5, p10, ap, r, r10, rStim);
                    sP5 += p5; sP10 += p10; sR += r; sRs += rStim; sR10 += r10; sAP += ap;

                    Set<String> fn = new HashSet<>(rel);
                    fn.removeAll(ranking);
                    mancati.put(q.getKey(), fn);
                }
            }
            int n = query.size();
            stampa("%-5s %5s %6s %6s %6.3f %6.3f %7.3f %6.3f %10.3f %8.3f   <- medie (media AP = MAP)%n",
                    "media", "", "", "", sP5 / n, sP10 / n, sAP / n, sR / n, sR10 / n, sRs / n);
        }
        return mancati;
    }

    /**
     * Recall stimata con il campione fuori dal pool.
     * rilevanti stimati = rilevanti del pool + (rilevanti nel campione / dimensione campione) * documenti fuori dal pool;
     * rilevanti trovati stimati = rilevanti del pool trovati + (frazione dei rilevanti del campione trovati) * stima fuori dal pool.
     */
    /** Numero stimato di documenti rilevanti: rilevanti del pool + stima di quelli fuori dal pool. */
    private static double rilevantiStimati(String q, Set<String> rel) {
        int relPool = 0, relCamp = 0;
        for (String f : rel) {
            if ("campione".equals(origine.get(q).get(f))) relCamp++; else relPool++;
        }
        int[] c = campione.get(q);
        return relPool + ((c == null || c[0] == 0) ? 0 : (double) relCamp / c[0] * c[1]);
    }

    private static double recallStimata(String q, Set<String> rel, Set<String> restituiti) {
        int relPool = 0, trovatiPool = 0, relCamp = 0, trovatiCamp = 0;
        for (String f : rel) {
            boolean trovato = restituiti.contains(f);
            if ("campione".equals(origine.get(q).get(f))) { relCamp++; if (trovato) trovatiCamp++; }
            else { relPool++; if (trovato) trovatiPool++; }
        }
        int[] c = campione.get(q);
        double fuori = (c == null || c[0] == 0) ? 0 : (double) relCamp / c[0] * c[1];
        double trovatiFuori = relCamp == 0 ? 0 : fuori * trovatiCamp / relCamp;
        double totale = relPool + fuori;
        return totale == 0 ? 0 : (trovatiPool + trovatiFuori) / totale;
    }

    /** Legge query.tsv: id -> query. */
    public static Map<String, String> leggiQuery(Path file) throws IOException {
        Map<String, String> query = new LinkedHashMap<>();
        for (String r : righe(file)) {
            String[] c = r.split("\t");
            query.put(c[0], c[1]);
        }
        return query;
    }

    /** Nome del file (senza cartella) del documento con l'id interno dato. */
    public static String nomeFile(IndexSearcher searcher, int doc) throws IOException {
        String percorso = searcher.storedFields().document(doc).get(Analizzatori.CAMPO_PERCORSO);
        // Taglio dopo l'ultimo separatore senza usare Paths: funziona con / e \ e con qualsiasi carattere
        return percorso.substring(Math.max(percorso.lastIndexOf('/'), percorso.lastIndexOf('\\')) + 1);
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
