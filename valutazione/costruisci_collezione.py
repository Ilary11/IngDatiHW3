"""Costruisce la collezione di valutazione a partire dagli appunti del corso.

Uso:  python costruisci_collezione.py <appunti.txt> <cartella_collezione> <cartella_valutazione>

<appunti.txt> e' il testo degli appunti del corso (95 pagine), esportato dal PDF con
`pdftotext appunti.pdf appunti.txt`.

Passi:
1. divide il testo nelle sezioni degli appunti, riconoscendo i titoli elencati in SEZIONI;
2. divide ogni sezione in file .txt di circa 120-250 parole, tagliando tra un paragrafo e l'altro;
3. scrive sezioni.tsv (file -> sezione da cui proviene);
4. scrive qrels.tsv: per ogni query, i file rilevanti, cioe' tutti i file delle sezioni
   che rispondono al bisogno informativo della query (vedi QUERY).
"""
import os
import re
import sys

# (id, inizio del titolo nel testo, nome breve usato nel nome dei file)
SEZIONI = [
    ("intro-1", '1. Perché questo corso', "introduzione"),
    ("intro-2", "2. Il docente e le lezioni", "docente-lezioni"),
    ("intro-3", "3. Programma", "programma"),
    ("intro-4", "4. Impostazione della didattica", "didattica-esame"),
    ("intro-5", "5. Motivazioni: i sistemi data-driven", "sistemi-data-driven"),
    ("intro-6", "6. Fase 1: acquisizione", "data-engineering"),
    ("intro-7", '7. "Data is food for AI"', "data-centric-ai"),
    ("etica-0", "8. Motivazioni: etica dei dati", "etica-dei-dati"),
    ("etica-1", "8.1 Ranking e selezione", "etica-ranking"),
    ("etica-2", "8.2 Classificazione", "etica-classificazione"),
    ("etica-3", "8.3 Trasparenza", "etica-trasparenza"),
    ("intro-9", "9. AI e Data Engineering", "ai-data-engineering"),
    ("intro-10", "10. L'elefante nella stanza", "llm-elefante"),
    ("hw-1", "📌 HOMEWORK 1", "homework-1"),
    ("di-0", "Data Integration (Ingegneria dei dati", "data-integration"),
    ("di-2", "2. Perché è difficile", "di-eterogeneita"),
    ("di-3", "3. Esempio guida: i film", "di-esempio-film"),
    ("di-4", "4. Le tre sfide", "di-tre-sfide"),
    ("di-4.1", "4.1 Ambiguità semantica", "di-ambiguita-semantica"),
    ("di-4.2", "4.2 Ambiguità di rappresentazione", "di-ambiguita-istanze"),
    ("di-4.3", "4.3 Inconsistenza dei dati", "di-inconsistenza"),
    ("di-5", "5. Secondo esempio", "di-specifiche-prodotto"),
    ("di-6", "6. La pipeline di data integration", "di-pipeline"),
    ("df-0", "Data fusion (panoramica)", "data-fusion-panoramica"),
    ("hw-di", "📝 Homework", "homework-data-integration"),
    ("sa-7", "PARTE A: SCHEMA ALIGNMENT", "sa-contesto"),
    ("sa-8", "8. Schema mediato", "sa-schema-mediato"),
    ("sa-9", "9. Attribute matching", "sa-attribute-matching"),
    ("sa-10", "10. Schema mapping", "sa-schema-mapping"),
    ("sa-11", "11. Tecniche di attribute matching", "sa-tecniche-matching"),
    ("rl-12", "PARTE B: RECORD LINKAGE", "rl-introduzione"),
    ("rl-13", "13. Definizione formale", "rl-definizione"),
    ("rl-14", "14. I passi del record linkage", "rl-passi"),
    ("rl-14.1", "14.1 Pairwise matching", "rl-pairwise-matching"),
    ("rl-14.2", "14.2 Clustering", "rl-clustering"),
    ("rl-14.3", "14.3 Blocking", "rl-blocking"),
    ("rl-14.4", "14.4 Eterogeneità di schema", "rl-schema-agnostic-blocking"),
    ("rl-14.5", "14.5 Meta-blocking", "rl-meta-blocking"),
    ("rl-14.6", "14.6 La nuova pipeline", "rl-nuova-pipeline"),
    ("rl-14.7", "14.7 Valutazione", "rl-valutazione"),
    ("rl-15", "15. LLM e nuovi approcci", "rl-llm"),
    ("df-c", "PARTE C: DATA FUSION", "data-fusion"),
    ("sem-0", "Approfondimento: Few-Label", "seminario-few-label"),
    ("sem-a1", "A.1 Ripasso", "seminario-er-classificazione"),
    ("sem-a2", "A.2 Data augmentation", "seminario-data-augmentation"),
    ("sem-a3", "A.3 Active learning", "seminario-active-learning"),
    ("sem-a4", "A.4 La ricerca del tutor", "seminario-ricerca-tutor"),
    ("hw-2", "APPUNTI LEZIONE 16/10", "appunti-lezioni"),
    ("ir-1", "Source Discovery: basi di Information", "ir-dove-siamo"),
    ("ir-2", "2. Che cos'è l'information retrieval", "ir-definizione"),
    ("ir-3", "3. La matrice di incidenza", "ir-matrice-incidenza"),
    ("ir-4", "4. L'indice invertito", "ir-indice-invertito"),
    ("ir-5", "5. Query booleane", "ir-query-booleane"),
    ("ir-6", "6. Token e termini", "ir-token-termini"),
    ("ir-7", "7. Merge più veloci", "ir-skip-pointer"),
    ("ir-8", "8. Query di frase", "ir-query-di-frase"),
    ("ir-9", "9. Strutture dati per il dizionario", "ir-dizionario"),
    ("ir-10", "10. Query con wildcard", "ir-wildcard"),
    ("ir-11", "11. Correzione ortografica", "ir-correzione-ortografica"),
    ("ir-12", "12. Ranked retrieval", "ir-ranked-retrieval"),
    ("ir-13", "13. Metriche: come si valuta", "ir-metriche"),
    ("ir-14", "14. Esempi e risorse", "ir-risorse"),
    ("lu-0", "Lucene & Elasticsearch", "lucene"),
    ("lu-1", "L.1 Che cos'è Apache Lucene", "lucene-introduzione"),
    ("lu-2", "L.2 Indicizzazione", "lucene-indicizzazione"),
    ("lu-3", "L.3 Analisi del testo", "lucene-analisi-testo"),
    ("lu-4", "L.4 Segmenti", "lucene-segmenti"),
    ("lu-5", "L.5 Ricerca", "lucene-ricerca"),
    ("lu-6", "L.6 Ricerca vettoriale", "lucene-ricerca-vettoriale"),
    ("lu-7", "L.7 Elasticsearch", "elasticsearch-distribuito"),
    ("lu-8", "L.8 Usare Elasticsearch", "elasticsearch-uso"),
    ("we-1", "Web Data Extraction (Ingegneria", "web-extraction"),
    ("we-2", "2. Input: che forma", "we-input"),
    ("we-3", "3. Output: tabella", "we-output"),
    ("we-4", "4. Scope: pagina", "we-scope"),
    ("we-5", "5. Siti data-intensive e wrapper", "we-wrapper"),
    ("we-6", "6. XPath", "we-xpath"),
    ("we-7", "7. Scrivere un wrapper a mano", "we-wrapper-manuale"),
    ("we-8", "8. Caso di studio 1: Vertex", "we-vertex"),
    ("we-9", "9. Caso di studio 2: Alfred", "we-alfred"),
    ("we-10", "10. Caso di studio 3: WEIR", "we-weir"),
    ("we-11", "11. Caso di studio 4: OpenCeres", "we-openceres"),
    ("we-12", "12. Web data extraction con gli LLM", "we-llm"),
]

# Le query di test: (id, query nella sintassi del programma, bisogno informativo, sezioni rilevanti).
# La rilevanza si giudica rispetto al bisogno informativo, non alle parole della query:
# un file e' rilevante se viene da una sezione che tratta l'argomento richiesto.
QUERY = [
    ("Q01", 'contenuto "meta-blocking"',
     "Come funziona il meta-blocking: grafo di blocking, pesatura e potatura degli archi",
     ["rl-14.5"]),
    ("Q02", 'contenuto "indice invertito"',
     "Che cos'e' un indice invertito, come si costruisce e come si usa per rispondere alle query",
     ["ir-4", "ir-5", "ir-7"]),
    ("Q03", "contenuto GAV LAV",
     "Gli approcci GAV, LAV e GLAV per definire lo schema mapping",
     ["sa-10"]),
    ("Q04", "contenuto precision recall motore ricerca",
     "Come si valuta la qualita' di un motore di ricerca (precision, recall, P@K, MAP)",
     ["ir-13"]),
    ("Q05", "contenuto equità",
     "Equita' (fairness) dei sistemi decisionali automatici: ranking, selezione, classificazione",
     ["etica-0", "etica-1", "etica-2"]),
    ("Q06", "contenuto wrapper XPath",
     "Come si estraggono dati da pagine web con wrapper basati su XPath",
     ["we-5", "we-6", "we-7"]),
    ("Q07", "contenuto analyzer",
     "Come Lucene analizza il testo: tokenizer, token filter e analyzer",
     ["lu-3"]),
    ("Q08", 'contenuto "data augmentation" "active learning"',
     "Come addestrare un matcher di entity resolution con poche etichette",
     ["sem-0", "sem-a2", "sem-a3"]),
    ("Q09", "contenuto tf-idf cosine",
     "Ranking dei documenti con pesi tf-idf e cosine similarity",
     ["ir-12"]),
    ("Q10", "contenuto shard repliche",
     "Come Elasticsearch distribuisce indice e ricerca su piu' nodi",
     ["lu-7"]),
]

PAROLE_MIN, PAROLE_MAX = 120, 250


def pulisci(righe):
    """Toglie intestazioni e numeri di pagina lasciati dall'esportazione del PDF."""
    out = []
    for r in righe:
        s = r.strip()
        if s == "INGEGNERIA DEI DATI" or re.fullmatch(r"\d{1,3}", s):
            continue
        out.append(r.rstrip())
    return out


def paragrafi(righe):
    blocchi, corrente = [], []
    for r in righe:
        if r.strip():
            corrente.append(r.strip())
        elif corrente:
            blocchi.append(" ".join(corrente))
            corrente = []
    if corrente:
        blocchi.append(" ".join(corrente))
    return blocchi


def spezza(blocchi):
    """Raggruppa i paragrafi in pezzi da PAROLE_MIN a circa PAROLE_MAX parole."""
    pezzi, corrente, n = [], [], 0
    for b in blocchi:
        frasi = [b] if len(b.split()) <= PAROLE_MAX else re.split(r"(?<=[.!?])\s+", b)
        for f in frasi:
            corrente.append(f)
            n += len(f.split())
            if n >= PAROLE_MIN:
                pezzi.append("\n".join(corrente))
                corrente, n = [], 0
    if corrente:
        if pezzi and n < 40:
            pezzi[-1] += "\n" + "\n".join(corrente)
        else:
            pezzi.append("\n".join(corrente))
    return pezzi


def main():
    if len(sys.argv) != 4:
        print(__doc__)
        sys.exit(1)
    appunti, cartella, cartella_val = sys.argv[1:]
    righe = open(appunti, encoding="utf-8").read().splitlines()

    # 1. inizio di ogni sezione, cercando i titoli in ordine
    inizi, pos = [], 0
    for sid, titolo, slug in SEZIONI:
        while pos < len(righe) and not righe[pos].strip().startswith(titolo):
            pos += 1
        if pos == len(righe):
            sys.exit(f"Titolo non trovato: {titolo}")
        inizi.append(pos)
    inizi.append(len(righe))

    os.makedirs(cartella, exist_ok=True)
    os.makedirs(cartella_val, exist_ok=True)
    file_di = {}
    with open(os.path.join(cartella_val, "sezioni.tsv"), "w", encoding="utf-8") as tsv:
        tsv.write("file\tsezione\n")
        for i, (sid, titolo, slug) in enumerate(SEZIONI):
            pezzi = spezza(paragrafi(pulisci(righe[inizi[i]:inizi[i + 1]])))
            for j, testo in enumerate(pezzi, 1):
                nome = f"{i + 1:02d}-{slug}-{j}.txt"
                with open(os.path.join(cartella, nome), "w", encoding="utf-8") as f:
                    f.write(testo + "\n")
                tsv.write(f"{nome}\t{sid}\n")
                file_di.setdefault(sid, []).append(nome)

    with open(os.path.join(cartella_val, "query.tsv"), "w", encoding="utf-8") as q, \
         open(os.path.join(cartella_val, "qrels.tsv"), "w", encoding="utf-8") as qr:
        q.write("id\tquery\tbisogno\n")
        qr.write("query\tfile\n")
        for qid, testo, bisogno, sezioni in QUERY:
            q.write(f"{qid}\t{testo}\t{bisogno}\n")
            for sid in sezioni:
                for nome in file_di[sid]:
                    qr.write(f"{qid}\t{nome}\n")

    n_file = sum(len(v) for v in file_di.values())
    print(f"Sezioni: {len(SEZIONI)}, file creati: {n_file}")


if __name__ == "__main__":
    main()
