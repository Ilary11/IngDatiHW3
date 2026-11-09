package it.uniroma3.idd;

import java.io.IOException;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilterFactory;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.pattern.PatternTokenizerFactory;

/**
 * Definisce gli analyzer usati per i due campi (nome e contenuto).
 *
 * IMPORTANTE: lo stesso analyzer va usato sia quando si indicizza sia quando
 * si interroga l'indice, altrimenti i termini della query non coincidono con
 * quelli salvati nell'indice. Per questo sta in una classe a parte, usata
 * sia da Indicizzatore che da Ricerca.
 */
public final class Analizzatori {

    public static final String CAMPO_NOME = "nome";
    public static final String CAMPO_CONTENUTO = "contenuto";
    public static final String CAMPO_PERCORSO = "percorso";

    private Analizzatori() {}

    /**
     * Analyzer per il NOME del file.
     * I nomi dei file sono brevi e usano separatori "strani":
     * "appunti_record-linkage.2024" deve diventare [appunti, record, linkage, 2024].
     *  - tokenizer a pattern: spezza su tutto ciò che non è lettera o cifra
     *    (underscore, trattino, punto, spazio...). Lo StandardTokenizer invece
     *    NON spezza sull'underscore.
     *  - lowercase: "Appunti" e "appunti" diventano uguali.
     *  - asciifolding: "perché" e "perche" diventano uguali.
     *  - niente stopword e niente stemming: nel nome ogni parola conta e le
     *    parole sono poche, quindi non vogliamo perderne o storpiarne nessuna.
     */
    public static Analyzer analyzerNome() throws IOException {
        return CustomAnalyzer.builder()
                .withTokenizer(PatternTokenizerFactory.class, "pattern", "[^\\p{L}\\p{N}]+")
                .addTokenFilter(LowerCaseFilterFactory.class)
                .addTokenFilter(ASCIIFoldingFilterFactory.class)
                .build();
    }

    /**
     * Analyzer per il CONTENUTO del file.
     * ItalianAnalyzer fa già tutto quello che serve per un testo in italiano:
     *  - StandardTokenizer (spezza il testo in parole);
     *  - gestione delle elisioni ("l'indice" -> "indice");
     *  - lowercase;
     *  - rimozione delle stopword italiane (il, di, che, ...);
     *  - stemming leggero ("indici", "indice" -> stessa radice).
     */
    public static Analyzer analyzerContenuto() {
        return new ItalianAnalyzer();
    }

    /** Un unico analyzer che sceglie quello giusto in base al campo. */
    public static Analyzer perCampo() throws IOException {
        return new PerFieldAnalyzerWrapper(
                analyzerContenuto(),                       // default
                Map.of(CAMPO_NOME, analyzerNome(),
                       CAMPO_CONTENUTO, analyzerContenuto()));
    }
}
