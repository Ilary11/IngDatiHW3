"""Estrae, per ogni query, un campione di documenti FUORI dal pool, da giudicare a mano.

Uso:  python valutazione/estrai_campione.py

Serve a stimare quanti documenti rilevanti il pool non ha visto (e quindi la recall).
Per ogni query si considerano i documenti della categoria tematica collegata al bisogno
informativo (da valutazione/elenco_voci.tsv) che non sono nel pool (valutazione/pool.tsv),
e se ne estraggono 10 a caso. Il seed e' fisso, quindi il campione e' riproducibile.
Scrive valutazione/campione.tsv: query, file, numero di documenti della categoria fuori dal pool.
"""
import csv
import os
import random

CARTELLA = os.path.dirname(os.path.abspath(__file__))
DIMENSIONE = 10
CATEGORIE = {
    "Q01": ["Motori di ricerca"],
    "Q02": ["Crittografia"],
    "Q03": ["Apprendimento automatico", "Intelligenza artificiale"],
    "Q04": ["Basi di dati"],
    "Q05": ["Formaggi italiani"],
    "Q06": ["Vulcani d'Italia"],
    "Q07": ["Pianeti del sistema solare"],
    "Q08": ["Malattie infettive"],
    "Q09": ["Pittori italiani del Rinascimento"],
    "Q10": ["Vitamine"],
}


def leggi(nome):
    with open(os.path.join(CARTELLA, nome), encoding="utf-8") as f:
        return list(csv.DictReader(f, delimiter="\t"))


def main():
    elenco = leggi("elenco_voci.tsv")
    pool = {}
    for r in leggi("pool.tsv"):
        pool.setdefault(r["query"], set()).add(r["file"])

    rnd = random.Random(2026)
    with open(os.path.join(CARTELLA, "campione.tsv"), "w", encoding="utf-8") as out:
        out.write("query\tfile\tdocumenti_categoria_fuori_pool\n")
        for q, categorie in CATEGORIE.items():
            candidati = sorted(r["file"] for r in elenco
                               if r["categoria"] in categorie and r["file"] not in pool[q])
            for f in rnd.sample(candidati, min(DIMENSIONE, len(candidati))):
                out.write(f"{q}\t{f}\t{len(candidati)}\n")
            print(f"{q}: {len(candidati)} documenti della categoria fuori dal pool, {DIMENSIONE} estratti")


if __name__ == "__main__":
    main()
