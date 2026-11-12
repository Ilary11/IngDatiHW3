# Homework 3 – Ingegneria dei Dati

Indicizzazione, ricerca e valutazione di file `.txt` con Apache Lucene 9.

Autrice: Ilaria Puliani – ila.puliani1@stud.uniroma3.it

## Cosa serve sul tuo computer
- Java 17 o più recente (`java -version`)
- Maven (`mvn -version`), oppure un IDE con Maven incluso (VS Code con Extension Pack for Java, IntelliJ, Eclipse)

## Contenuto del repository
| Percorso | Cosa contiene |
|---|---|
| `src/main/java/it/uniroma3/idd/` | `Analizzatori`, `Indicizzatore`, `Ricerca`, `Valutazione` |
| `dati_di_prova/` | 12 file scritti a mano, per provare le funzionalità |
| `collezione_appunti/` | 200 file di testo reale (appunti del corso), per la valutazione |
| `valutazione/` | query, giudizi di rilevanza, script di costruzione della collezione, risultati |
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
```
mvn compile exec:java -Dexec.mainClass=it.uniroma3.idd.Valutazione
```
Indicizza `collezione_appunti` in memoria due volte (analyzer del progetto e, come baseline, `StandardAnalyzer`
sul contenuto), esegue le 10 query di `valutazione/query.tsv` e calcola per ognuna precision, recall, F1, P@10
e average precision, più le medie (MAP). Elenca anche i documenti rilevanti non trovati.
I risultati vengono salvati in `valutazione/risultati.txt`.

### Come è costruita la collezione di valutazione
`valutazione/costruisci_collezione.py` prende il testo degli appunti del corso (esportato dal PDF con `pdftotext`),
lo divide in 83 sezioni riconoscendone i titoli e spezza ogni sezione in file da circa 120–250 parole.
`valutazione/sezioni.tsv` dice da quale sezione viene ogni file.
Per ogni query è definito un bisogno informativo e le sezioni che lo soddisfano: tutti i file di quelle sezioni
sono rilevanti (`valutazione/qrels.tsv`), gli altri no.
```
python valutazione/costruisci_collezione.py appunti.txt collezione_appunti valutazione
```

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
