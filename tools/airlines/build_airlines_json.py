#!/usr/bin/env python3
"""Gera app/src/main/assets/airlines.json a partir do airlines.dat do OpenFlights.

A tabela de operadores é dado de referência estático (ver AD-007 no CLAUDE.md): resolve o nome da
companhia a partir do prefixo de 3 letras do indicativo de voo, sem qualquer pedido de rede
(FR-011). Atualizá-la é manutenção periódica, e é por isso que a conversão vive num script em vez
de num ficheiro escrito à mão.

Fonte: https://github.com/jpatokal/openflights (data/airlines.dat), sob Open Database License.
A ODbL exige atribuição: ver app/src/main/assets/airlines-LICENSE.txt.

Uso:
    python3 tools/airlines/build_airlines_json.py            # descarrega e converte
    python3 tools/airlines/build_airlines_json.py --input tools/airlines/airlines.dat
"""

import argparse
import csv
import json
import pathlib
import sys
import urllib.request

SOURCE_URL = "https://raw.githubusercontent.com/jpatokal/openflights/master/data/airlines.dat"

# Colunas do airlines.dat, por posição.
NAME, ICAO, ACTIVE = 1, 4, 7

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
DEFAULT_OUTPUT = REPO_ROOT / "app/src/main/assets/airlines.json"


def is_valid_icao(code: str) -> bool:
    """Um designador ICAO são exatamente 3 letras A-Z maiúsculas.

    Descarta de uma vez os marcadores de ausência do dataset (`\\N`, `-`, `N/A`), os campos vazios
    e os códigos IATA de 2 letras que às vezes aparecem trocados de coluna.
    """
    return len(code) == 3 and code.isascii() and code.isalpha() and code.isupper()


def build_table(rows) -> dict[str, str]:
    """Indexa os registos por designador ICAO.

    Em código repetido ganha a companhia marcada como ativa: o dataset é antigo e contém
    operadores extintos cujo designador já foi reatribuído. Não se filtra por `Active`, porém —
    marcar como inativa uma companhia que ainda voa custaria cobertura em SC-004, enquanto um
    designador extinto que ninguém usa apenas nunca chega a ser consultado.
    """
    table: dict[str, str] = {}
    active: set[str] = set()

    for row in rows:
        if len(row) <= ACTIVE:
            continue
        code, name = row[ICAO].strip(), row[NAME].strip()
        if not is_valid_icao(code) or not name:
            continue
        is_active = row[ACTIVE].strip() == "Y"
        if code in table and not (is_active and code not in active):
            continue
        table[code] = name
        if is_active:
            active.add(code)

    return table


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=pathlib.Path, help="airlines.dat local; omitir para descarregar")
    parser.add_argument("--output", type=pathlib.Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    if args.input:
        text = args.input.read_text(encoding="utf-8")
    else:
        print(f"A descarregar {SOURCE_URL}", file=sys.stderr)
        with urllib.request.urlopen(SOURCE_URL, timeout=60) as response:
            text = response.read().decode("utf-8")

    table = build_table(csv.reader(text.splitlines()))
    if len(table) < 1000:
        # O mesmo limiar de AirlineTableCoverageTest: falhar aqui em vez de gerar um asset pobre.
        return f"ERRO: apenas {len(table)} operadores — a fonte ou o filtro estão errados."

    # Uma entrada por linha e chaves ordenadas: o asset é commitado, e um diff legível é o que
    # torna revisível uma atualização da tabela.
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(table, ensure_ascii=False, indent=1, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(f"{len(table)} operadores escritos em {args.output}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
