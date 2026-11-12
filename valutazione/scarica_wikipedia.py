"""Scarica da Wikipedia in italiano la collezione di documenti per la valutazione.

Uso:  python valutazione/scarica_wikipedia.py collezione_wikipedia

Ogni documento e' l'introduzione (la parte iniziale, prima del sommario) di una voce di
Wikipedia, salvata come <titolo della voce>.txt. Le voci vengono da due fonti:
  1. le voci di alcune categorie tematiche (e delle loro sottocategorie dirette): qui
     cadranno i documenti rilevanti per le query di valutazione;
  2. voci casuali, per avere una collezione grande e varia, come un indice reale.
L'elenco delle voci scaricate (file, titolo, categoria di provenienza) va in valutazione/elenco_voci.tsv.
Si scartano le voci con meno di PAROLE_MIN parole (abbozzi) e le pagine di disambiguazione.

Usa solo la libreria standard di Python (nessun pip install).
Fonte: Wikipedia, licenza CC BY-SA 4.0 (https://it.wikipedia.org).
"""
import json
import os
import random
import re
import sys
import time
import urllib.parse
import urllib.request

API = "https://it.wikipedia.org/w/api.php"
USER_AGENT = "IngDatiHW3/1.0 (homework universitario Ingegneria dei Dati; https://github.com/Ilary11/IngDatiHW3)"

CATEGORIE = [
    # informatica e dati
    "Motori di ricerca", "Basi di dati", "Apprendimento automatico", "Intelligenza artificiale",
    "Elaborazione del linguaggio naturale", "Crittografia", "Linguaggi di programmazione",
    "Sistemi operativi", "Reti di calcolatori", "Algoritmi", "Strutture dati",
    # altri argomenti, per una collezione varia
    "Primi piatti", "Formaggi italiani", "Dolci italiani", "Vulcani d'Italia",
    "Pianeti del sistema solare", "Malattie infettive", "Vitamine",
    "Pittori italiani del Rinascimento", "Opere liriche di Giuseppe Verdi",
    "Romanzi di Italo Calvino", "Sport olimpici", "Energie rinnovabili",
]
MAX_PER_CATEGORIA = 150
VOCI_CASUALI = 2500
PAROLE_MIN = 40


def chiama(parametri):
    parametri = dict(parametri, format="json", formatversion="2", maxlag="5")
    url = API + "?" + urllib.parse.urlencode(parametri)
    for tentativo in range(5):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=30) as r:
                dati = json.loads(r.read().decode("utf-8"))
            if "error" in dati and dati["error"].get("code") == "maxlag":
                time.sleep(5)
                continue
            return dati
        except Exception as e:  # rete instabile: riprovo
            print(f"  errore di rete ({e}), riprovo...")
            time.sleep(3 * (tentativo + 1))
    raise RuntimeError("Wikipedia non risponde, riprova piu' tardi")


def membri(categoria, tipo):
    """Titoli delle pagine (tipo='page') o sottocategorie (tipo='subcat') di una categoria."""
    titoli, cont = [], {}
    while True:
        d = chiama(dict(action="query", list="categorymembers", cmtitle="Categoria:" + categoria,
                        cmtype=tipo, cmlimit="500", cmnamespace="14" if tipo == "subcat" else "0", **cont))
        titoli += [m["title"] for m in d.get("query", {}).get("categorymembers", [])]
        if "continue" not in d:
            return titoli
        cont = {k: v for k, v in d["continue"].items()}


def introduzioni(titoli):
    """Restituisce {titolo: testo dell'introduzione}, 20 voci per richiesta."""
    testi = {}
    for i in range(0, len(titoli), 20):
        d = chiama(dict(action="query", prop="extracts|pageprops", exintro="1", explaintext="1",
                        exlimit="20", redirects="1", titles="|".join(titoli[i:i + 20])))
        for p in d.get("query", {}).get("pages", []):
            if "disambiguation" in p.get("pageprops", {}):
                continue
            testo = (p.get("extract") or "").strip()
            if len(testo.split()) >= PAROLE_MIN:
                testi[p["title"]] = testo
        time.sleep(0.2)
    return testi


def casuali(n):
    titoli = set()
    while len(titoli) < n:
        d = chiama(dict(action="query", list="random", rnnamespace="0", rnlimit="20"))
        titoli.update(p["title"] for p in d["query"]["random"])
        time.sleep(0.1)
    return sorted(titoli)


def nome_file(titolo):
    return re.sub(r'[\\/:*?"<>|]', "_", titolo).strip(". ") + ".txt"


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(1)
    uscita = sys.argv[1]
    os.makedirs(uscita, exist_ok=True)
    random.seed(42)

    da_categorie = {}
    for cat in CATEGORIE:
        titoli = membri(cat, "page")
        for sub in membri(cat, "subcat")[:20]:
            titoli += membri(sub.split(":", 1)[1], "page")
        titoli = sorted(set(titoli))
        random.shuffle(titoli)
        da_categorie[cat] = titoli[:MAX_PER_CATEGORIA]
        print(f"Categoria {cat}: {len(titoli)} voci trovate, ne prendo {len(da_categorie[cat])}")

    tutti = sorted({t for ts in da_categorie.values() for t in ts})
    print(f"Voci dalle categorie: {len(tutti)}. Scarico anche {VOCI_CASUALI} voci casuali...")
    tutti = sorted(set(tutti) | set(casuali(VOCI_CASUALI)))

    print(f"Scarico le introduzioni di {len(tutti)} voci (qualche minuto)...")
    testi = introduzioni(tutti)

    elenco = os.path.join(os.path.dirname(os.path.abspath(__file__)), "elenco_voci.tsv")
    with open(elenco, "w", encoding="utf-8") as tsv:
        tsv.write("file\ttitolo\tcategoria\n")
        origine = {t: c for c, ts in da_categorie.items() for t in ts}
        usati = set()  # Windows non distingue maiuscole e minuscole nei nomi dei file
        for titolo, testo in sorted(testi.items()):
            nome = nome_file(titolo)
            while nome.lower() in usati:
                nome = nome[:-4] + "_bis.txt"
            usati.add(nome.lower())
            with open(os.path.join(uscita, nome), "w", encoding="utf-8") as f:
                f.write(testo + "\n")
            tsv.write(f"{nome}\t{titolo}\t{origine.get(titolo, 'casuale')}\n")
    print(f"Fatto: {len(testi)} documenti salvati in {uscita}")


if __name__ == "__main__":
    main()
