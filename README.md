# Homework 3 – Ingegneria dei Dati

Indicizzazione, ricerca e valutazione di file `.txt` con Apache Lucene 9.

Autrice: Ilaria Puliani – ila.puliani1@stud.uniroma3.it

## Cosa serve sul tuo computer
- Java 17 o più recente (`java -version`)
- Maven (`mvn -version`), oppure un IDE con Maven incluso (VS Code con Extension Pack for Java, IntelliJ, Eclipse)

## Contenuto del repository
| Percorso | Cosa contiene |
|---|---|
| `src/main/java/it/uniroma3/idd/` | `Analizzatori`, `Indicizzatore`, `Ricerca` (i due programmi richiesti), `Valutazione`, `Pooling`, `Configurazioni` |
| `dati_di_prova/` | 12 file scritti a mano, per provare le funzionalità |
| `collezione_wikipedia/` | 2.667 introduzioni di voci di Wikipedia in italiano, per la valutazione |
| `valutazione/` | script di download, query, pool, campione, giudizi di rilevanza, risultati |
| `genera_corpus.py` | generatore di corpus sintetici, per misurare i tempi su 100–10.000 file |

## 1. Indicizzare
```
mvn compile exec:java -Dexec.mainClass=it.uniroma3.idd.Indicizzatore -Dexec.args="dati_di_prova"
```
Al posto di `dati_di_prova` puoi mettere qualsiasi cartella del tuo computer che contiene file `.txt`.
Il programma stampa il numero di file indicizzati e il tempo impiegato.
L'indice viene salvato nella cartella `indice`.

## 2. Cercare
```
mvn compile exec:java -Dexec.mainClass=it.uniroma3.idd.Ricerca
```
Poi scrivi le query, una per riga:
```
query> nome blocking
query> contenuto "indice invertito"
query> esci
```

## 3. Valutare
La valutazione segue il metodo visto a lezione (collezione, query, giudizi di rilevanza) con il **pooling** di TREC.

1. **Collezione** – `valutazione/scarica_wikipedia.py` scarica le introduzioni di voci di Wikipedia in italiano
   (alcune categorie tematiche più voci casuali) e le salva in `collezione_wikipedia/`; l'elenco è in `valutazione/elenco_voci.tsv`.
   ```
   python valutazione/scarica_wikipedia.py collezione_wikipedia
   ```
2. **Query** – `valutazione/query.tsv`: 10 query, ognuna con il suo bisogno informativo.
3. **Pool** – `Pooling` prende i primi 10 risultati di tre configurazioni (sistema, baseline con `StandardAnalyzer`,
   ricerca su nome+contenuto) e scrive l'unione in `valutazione/pool.tsv` (139 coppie da giudicare).
   ```
   mvn compile exec:java -Dexec.mainClass=it.uniroma3.idd.Pooling
   ```
4. **Campione fuori dal pool** – `valutazione/campione.tsv`: per ogni query, 10 documenti estratti a caso dalla categoria
   tematica e non presenti nel pool, per stimare quanti rilevanti il pool non vede.
   ```
   python valutazione/estrai_campione.py
   ```
5. **Giudizi** – `valutazione/qrels.tsv`: 239 giudizi manuali (1 = rilevante, 0 = no), dati leggendo il testo del documento
   rispetto al bisogno informativo.
6. **Metriche** – `Valutazione` calcola P@5, P@10, AP e MAP, recall rispetto ai rilevanti noti, recall@10 stimata e recall
   stimata, per le tre configurazioni, e salva tutto in `valutazione/risultati.txt`.
   ```
   mvn compile exec:java -Dexec.mainClass=it.uniroma3.idd.Valutazione
   ```

I testi in `collezione_wikipedia/` provengono da [Wikipedia](https://it.wikipedia.org) e sono distribuiti con licenza
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.it); il titolo di ogni voce è il nome del file.

## Misurare i tempi su collezioni più grandi
Lo script `genera_corpus.py` genera un corpus sintetico riproducibile (seed fisso), usando il vocabolario di `dati_di_prova`:
```
python genera_corpus.py corpus_1000 1000
mvn compile exec:java -Dexec.mainClass=it.uniroma3.idd.Indicizzatore -Dexec.args="corpus_1000 indice_1000"
```

## Query di prova (su `dati_di_prova`)
| # | Query | Che cosa verifica |
|---|-------|-------------------|
| 1 | `nome blocking` | divisione del nome sul trattino e sullo spazio: trova `record-linkage-blocking` e `Meta Blocking` |
| 2 | `nome META` | il lowercase: trova `Meta Blocking` |
| 3 | `nome schema alignment` | più termini in OR: i file con entrambi i termini vengono prima |
| 4 | `nome "record linkage"` | phrase query sul nome |
| 5 | `contenuto blocking` | ricerca semplice nel contenuto |
| 6 | `contenuto indici` | lo stemming: trova `indice_invertito` anche se nel testo c'è "indice" |
| 7 | `contenuto "indice invertito"` | phrase query: le parole devono essere vicine e nello stesso ordine |
| 8 | `contenuto "invertito indice"` | phrase query con l'ordine sbagliato: nessun risultato |
| 9 | `contenuto pecorino AND uova` | AND: solo i documenti che contengono entrambi i termini |
| 10 | `contenuto il di che` | solo stopword: la query diventa vuota e non trova nulla |
| 11 | `contenuto wrapper -language` | NOT: i documenti con "wrapper" ma senza "language", quindi esclude `LLM-per-estrazione` |
| 12 | `contenuto ambiguita` | senza accento trova comunque `appunti_data-integration`: lo stemmer italiano toglie gli accenti |
