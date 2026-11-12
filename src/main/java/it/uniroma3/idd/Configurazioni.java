package it.uniroma3.idd;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;

/**
 * Le tre configurazioni del motore di ricerca confrontate nella valutazione.
 *  - SISTEMA:      gli analyzer del progetto (ItalianAnalyzer sul contenuto), query sul campo indicato;
 *  - BASELINE:     StandardAnalyzer sul contenuto (niente stemming, stopword, elisioni);
 *  - NOME+CONTENUTO: gli analyzer del progetto, ma i termini vengono cercati sia nel nome del
 *                  file (il titolo della voce) sia nel contenuto.
 * Le usano sia Pooling (per costruire il campione da giudicare) sia Valutazione.
 */
public final class Configurazioni {

    private Configurazioni() {}

    public static final class Configurazione {
        public final String nome;
        public final Analyzer analyzer;
        private final boolean nomeEContenuto;

        Configurazione(String nome, Analyzer analyzer, boolean nomeEContenuto) {
            this.nome = nome;
            this.analyzer = analyzer;
            this.nomeEContenuto = nomeEContenuto;
        }

        /** Costruisce la query a partire da una riga nella sintassi di Ricerca ("contenuto termini..."). */
        public Query query(String riga) throws ParseException {
            if (!nomeEContenuto) {
                return Ricerca.costruisciQuery(riga, analyzer);
            }
            String[] parti = riga.trim().split("\\s+", 2);
            String[] campi = {Analizzatori.CAMPO_NOME, Analizzatori.CAMPO_CONTENUTO};
            return new MultiFieldQueryParser(campi, analyzer).parse(parti[1]);
        }
    }

    public static List<Configurazione> tutte() throws IOException {
        Analyzer baseline = new PerFieldAnalyzerWrapper(new StandardAnalyzer(),
                Map.of(Analizzatori.CAMPO_NOME, Analizzatori.analyzerNome(),
                       Analizzatori.CAMPO_CONTENUTO, new StandardAnalyzer()));
        return List.of(
                new Configurazione("SISTEMA (ItalianAnalyzer)", Analizzatori.perCampo(), false),
                new Configurazione("BASELINE (StandardAnalyzer)", baseline, false),
                new Configurazione("NOME+CONTENUTO", Analizzatori.perCampo(), true));
    }
}
