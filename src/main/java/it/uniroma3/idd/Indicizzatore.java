package it.uniroma3.idd;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * PRIMO PROGRAMMA: indicizza i file .txt di una cartella.
 *
 * Uso: mvn compile exec:java -Dexec.mainClass=it.uniroma3.idd.Indicizzatore
 *          -Dexec.args="<cartella con i .txt> [cartella dell'indice]"
 * Senza argomenti indicizza la cartella dati_di_prova.
 *
 * Per ogni file crea un Document Lucene con tre campi:
 *  - nome      (TextField, analizzato con l'analyzer per i nomi)
 *  - contenuto (TextField, analizzato con ItalianAnalyzer)
 *  - percorso  (StringField, NON analizzato: serve solo per stampare il risultato)
 */
public class Indicizzatore {

    public static void main(String[] args) throws IOException {
        // Senza argomenti usa la cartella di prova del progetto
        Path cartellaTxt = Paths.get(args.length > 0 ? args[0] : "dati_di_prova");
        Path cartellaIndice = Paths.get(args.length > 1 ? args[1] : "indice");

        // 1. Trovo tutti i .txt (anche nelle sottocartelle)
        List<Path> files = trovaFileTxt(cartellaTxt);
        System.out.println("Trovati " + files.size() + " file .txt in " + cartellaTxt.toAbsolutePath());

        // 2. Indicizzo su disco, misurando il tempo
        long inizio = System.nanoTime();
        int indicizzati;
        try (Directory dir = FSDirectory.open(cartellaIndice)) {
            indicizzati = indicizza(files, dir, Analizzatori.perCampo());
        }
        long millis = (System.nanoTime() - inizio) / 1_000_000;

        System.out.println("File indicizzati: " + indicizzati);
        System.out.println("Tempo di indicizzazione: " + millis + " ms");
        System.out.println("Indice salvato in: " + cartellaIndice.toAbsolutePath());
    }

    /** Restituisce tutti i file .txt della cartella, anche nelle sottocartelle, in ordine di nome. */
    public static List<Path> trovaFileTxt(Path cartella) throws IOException {
        try (Stream<Path> s = Files.walk(cartella)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".txt"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /**
     * Indicizza i file nella Directory indicata usando l'analyzer dato.
     * CREATE = se l'indice esiste già, lo ricrea da zero. Restituisce il numero di file indicizzati.
     * È un metodo a parte perché lo usa anche il programma di Valutazione.
     */
    public static int indicizza(List<Path> files, Directory dir, Analyzer analyzer) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        int indicizzati = 0;

        try (IndexWriter writer = new IndexWriter(dir, config)) {
            for (Path file : files) {
                // Nome senza estensione: ".txt" non è informativo, è in tutti i file
                String nomeFile = file.getFileName().toString();
                String nomeSenzaEstensione = nomeFile.substring(0, nomeFile.length() - 4);

                // new String(bytes, UTF_8) non si blocca se il file ha caratteri non UTF-8
                String contenuto = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

                Document doc = new Document();
                doc.add(new TextField(Analizzatori.CAMPO_NOME, nomeSenzaEstensione, Field.Store.YES));
                doc.add(new TextField(Analizzatori.CAMPO_CONTENUTO, contenuto, Field.Store.NO));
                doc.add(new StringField(Analizzatori.CAMPO_PERCORSO, file.toAbsolutePath().toString(), Field.Store.YES));

                writer.addDocument(doc);
                indicizzati++;
            }
            writer.commit();
        }
        return indicizzati;
    }
}
