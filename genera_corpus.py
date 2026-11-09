"""Genera un corpus sintetico di file .txt per misurare i tempi di indicizzazione.

Uso:  python genera_corpus.py <cartella_di_output> <numero_di_file>

Il vocabolario e' preso dai file di dati_di_prova; le parole vengono estratte
con una distribuzione di Zipf (poche parole molto frequenti, molte rare), come
succede nei testi reali. Il seed e' fisso, quindi il corpus e' riproducibile.
"""
import os
import random
import re
import sys

CARTELLA_PROVA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "dati_di_prova")


def vocabolario():
    parole = set()
    for nome in os.listdir(CARTELLA_PROVA):
        with open(os.path.join(CARTELLA_PROVA, nome), encoding="utf-8") as f:
            parole.update(w.lower() for w in re.findall(r"[^\W\d_]+", f.read()))
    return sorted(parole)


def main():
    if len(sys.argv) != 3:
        print("Uso: python genera_corpus.py <cartella_di_output> <numero_di_file>")
        sys.exit(1)
    uscita, n = sys.argv[1], int(sys.argv[2])
    os.makedirs(uscita, exist_ok=True)

    rnd = random.Random(42)
    voc = vocabolario()
    rnd.shuffle(voc)
    pesi = [1 / (i + 1) for i in range(len(voc))]  # legge di Zipf
    separatori = ["_", "-", " "]

    for i in range(n):
        parole_nome = rnd.sample(voc, rnd.randint(2, 4))
        nome = rnd.choice(separatori).join(parole_nome) + f"_{i:05d}.txt"
        n_parole = rnd.randint(200, 2000)
        testo = rnd.choices(voc, weights=pesi, k=n_parole)
        righe = [" ".join(testo[j:j + 15]) for j in range(0, n_parole, 15)]
        with open(os.path.join(uscita, nome), "w", encoding="utf-8") as f:
            f.write("\n".join(righe) + "\n")
    print(f"Creati {n} file in {uscita}")


if __name__ == "__main__":
    main()
