# Homework 3 – Ingegneria dei Dati

Indicizzazione e ricerca di file `.txt` con Apache Lucene 9.

## Cosa serve sul tuo computer
- Java 17 o più recente (`java -version`)
- Maven (`mvn -version`), oppure IntelliJ IDEA, che ha Maven incluso

## 1. Indicizzare
```
mvn compile exec:java -Dexec.mainClass=it.uniroma3.idd.Indicizzatore -Dexec.args="dati_di_prova"
```
Al posto di `dati_di_prova` puoi mettere qualsiasi cartella del tuo computer che contiene file `.txt`.
Il programma stampa il numero di file indicizzati e il tempo impiegato, che servono per la relazione.
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

## Query di test (su `dati_di_prova`)
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
