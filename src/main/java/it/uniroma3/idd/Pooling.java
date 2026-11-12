package it.uniroma3.idd;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * QUARTO PROGRAMMA: costruisce il "pool" di documenti da giudicare a mano.
 *
 * Uso: mvn compile exec:java -Dexec.mainClass=it.uniroma3.idd.Pooling
 *          [-Dexec.args="<collezione> <cartella valutazione> <profondita'>"]
 * Default: collezione_wikipedia, valutazione, 10.
 *
 * Con migliaia di documenti non si possono giudicare tutte le coppie query-documento.
 * Come in TREC, per ogni query si prendono i primi K risultati di ciascuna configurazione
 * (Configurazioni.tutte()) e si giudica solo la loro unione: il pool.
 * Il programma scrive valutazione/pool.tsv (query, file, posizione nelle tre configurazioni);
 * i giudizi (1 = rilevante, 0 = non rilevante) vanno poi in valutazione/qrels.tsv.
 */
public class Pooling {

    public static void main(String[] args) throws IOException, ParseException {
        Path collezione = Paths.get(args.length > 0 ? args[0] : "collezione_wikipedia");
        Path cartellaVal = Paths.get(args.length > 1 ? args[1] : "valutazione");
        int k = args.length > 2 ? Integer.parseInt(args[2]) : 10;

        Map<String, String> query = Valutazione.leggiQuery(cartellaVal.resolve("query.tsv"));
        List<Path> files = Indicizzatore.trovaFileTxt(collezione);
        List<Configurazioni.Configurazione> conf = Configurazioni.tutte();

        // pool.get(query).get(file) = posizione del file nelle varie configurazioni ("-" se assente)
        Map<String, Map<String, String[]>> pool = new LinkedHashMap<>();
        for (String q : query.keySet()) pool.put(q, new TreeMap<>());

        for (int c = 0; c < conf.size(); c++) {
            try (Directory dir = new ByteBuffersDirectory()) {
                Indicizzatore.indicizza(files, dir, conf.get(c).analyzer);
                try (DirectoryReader reader = DirectoryReader.open(dir)) {
                    IndexSearcher searcher = new IndexSearcher(reader);
                    for (Map.Entry<String, String> q : query.entrySet()) {
                        ScoreDoc[] top = searcher.search(conf.get(c).query(q.getValue()), k).scoreDocs;
                        for (int i = 0; i < top.length; i++) {
                            String f = Valutazione.nomeFile(searcher, top[i].doc);
                            String[] pos = pool.get(q.getKey()).computeIfAbsent(f, x -> new String[]{"-", "-", "-"});
                            pos[c] = String.valueOf(i + 1);
                        }
                    }
                }
            }
        }

        int totale = 0;
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(cartellaVal.resolve("pool.tsv"), StandardCharsets.UTF_8))) {
            out.println("query\tfile\tpos_sistema\tpos_baseline\tpos_nome_contenuto");
            for (Map.Entry<String, Map<String, String[]>> e : pool.entrySet()) {
                for (Map.Entry<String, String[]> d : e.getValue().entrySet()) {
                    out.println(e.getKey() + "\t" + d.getKey() + "\t" + String.join("\t", d.getValue()));
                    totale++;
                }
                System.out.printf("%s: %d documenti nel pool%n", e.getKey(), e.getValue().size());
            }
        }
        System.out.printf("Pool di profondita' %d su %d configurazioni: %d coppie da giudicare (su %d possibili)%n",
                k, conf.size(), totale, query.size() * files.size());
    }
}
