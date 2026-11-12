package it.uniroma3.idd;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Scanner;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

/**
 * SECONDO PROGRAMMA: legge le query da console e interroga l'indice.
 *
 * Uso: mvn compile exec:java -Dexec.mainClass=it.uniroma3.idd.Ricerca
 *          [-Dexec.args="cartella_indice"]
 *
 * Sintassi della query: <campo> <termini>
 *   nome record linkage
 *   contenuto indice invertito
 *   contenuto "indice invertito"        <- phrase query (parole vicine e in ordine)
 *   contenuto blocking AND clustering   <- entrambi i termini obbligatori
 * Scrivere "esci" per terminare.
 */
public class Ricerca {

    private static final int MAX_RISULTATI = 10;

    public static void main(String[] args) throws IOException {
        String cartellaIndice = args.length > 0 ? args[0] : "indice";
        Analyzer analyzer = Analizzatori.perCampo();

        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(cartellaIndice)));
             Scanner console = new Scanner(System.in)) {

            IndexSearcher searcher = new IndexSearcher(reader);
            System.out.println("Indice aperto: " + reader.numDocs() + " documenti.");
            System.out.println("Scrivi: nome <termini>  oppure  contenuto <termini>  (\"esci\" per uscire)");

            while (true) {
                System.out.print("\nquery> ");
                if (!console.hasNextLine()) break;
                String riga = console.nextLine().trim();
                if (riga.isEmpty()) continue;
                if (riga.equalsIgnoreCase("esci")) break;

                Query query;
                try {
                    query = costruisciQuery(riga, analyzer);
                } catch (IllegalArgumentException e) {
                    System.out.println(e.getMessage());
                    continue;
                } catch (ParseException e) {
                    System.out.println("Query non valida: " + e.getMessage());
                    continue;
                }
                // Stampo la query dopo l'analyzer: utile per capire stemming e stopword
                System.out.println("Query interpretata come: " + query);

                TopDocs risultati = searcher.search(query, MAX_RISULTATI);
                System.out.println("Documenti trovati: " + risultati.totalHits.value);

                int pos = 1;
                for (ScoreDoc sd : risultati.scoreDocs) {
                    Document doc = searcher.storedFields().document(sd.doc);
                    System.out.printf("%2d. [score %.3f] %s%n", pos++, sd.score,
                            doc.get(Analizzatori.CAMPO_PERCORSO));
                }
            }
        }
        System.out.println("Ciao!");
    }

    /**
     * Trasforma una riga come  contenuto "indice invertito"  in un oggetto Query.
     * La prima parola è il campo, il resto va al QueryParser, che usa lo stesso analyzer
     * dell'indicizzazione e riconosce da solo le virgolette (phrase query) e AND/OR/NOT/+/-.
     * È un metodo a parte perché lo usa anche il programma di Valutazione.
     */
    public static Query costruisciQuery(String riga, Analyzer analyzer) throws ParseException {
        String[] parti = riga.trim().split("\\s+", 2);
        String campo = parti[0].toLowerCase();
        if (!campo.equals(Analizzatori.CAMPO_NOME) && !campo.equals(Analizzatori.CAMPO_CONTENUTO)) {
            throw new IllegalArgumentException("La query deve iniziare con 'nome' o 'contenuto'.");
        }
        if (parti.length < 2) {
            throw new IllegalArgumentException("Mancano i termini da cercare.");
        }
        return new QueryParser(campo, analyzer).parse(parti[1]);
    }
}
