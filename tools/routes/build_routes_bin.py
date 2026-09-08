#!/usr/bin/env python3
"""Gera a tabela de rotas da mySky a partir dos dados do Virtual Radar Server.

Produz `app/src/main/assets/routes.bin`: registos de largura fixa, ordenados por indicativo, que a
app lê por pesquisa binária sem carregar nada para memória (AD-013).

A conversão acontece aqui e em mais lado nenhum. A app nunca lê CSV nem fala com o espelho dos
dados: descarrega este ficheiro já pronto (AD-014). Reimplementar a junção, a filtragem e a
ordenação em Kotlin seria ter a mesma regra em duas linguagens, a divergir em silêncio.

Fonte: https://github.com/vradarserver/standing-data — licença CC0-1.0 (domínio público).
Espelho com CSVs em bloco, atualizado de hora a hora: https://vrs-standing-data.adsb.lol/

Uso:
    python3 tools/routes/build_routes_bin.py
    python3 tools/routes/build_routes_bin.py --input-dir /tmp/cache   # sem descarregar
"""

from __future__ import annotations

import argparse
import csv
import io
import struct
import sys
import time
import urllib.request
from pathlib import Path

MIRROR = "https://vrs-standing-data.adsb.lol"

MAGIC = b"MYSKYRT\0"
FORMAT_VERSION = 1
HEADER_SIZE = 32
CALLSIGN_WIDTH = 7
IATA_WIDTH = 3
RECORD_SIZE = CALLSIGN_WIDTH + 2 * IATA_WIDTH

# Abaixo disto o ficheiro não é plausível e provavelmente a fonte mudou de forma.
MIN_EXPECTED_ROUTES = 100_000

# Preenchimento do indicativo. Tem de ser menor, em byte, do que qualquer carácter válido de uma
# chave (dígitos começam em 0x30, letras em 0x41) para a ordenação do ficheiro coincidir com a
# comparação byte a byte que a pesquisa binária faz.
PAD = b" "


def normalize_callsign(raw: str) -> str | None:
    """A mesma regra que `Route.callsignKeyOf` aplica na app.

    As duas têm de coincidir exatamente. Se divergirem, gera-se uma tabela inteira de rotas que a
    app nunca encontra — e nada, em lado nenhum, dá erro.
    """
    key = raw.strip().upper()
    if not key or len(key) > CALLSIGN_WIDTH:
        return None
    if not key.isalnum() or not key.isascii():
        return None
    return key


def fetch(name: str, input_dir: Path | None) -> str:
    if input_dir is not None:
        return (input_dir / name).read_text(encoding="utf-8-sig")
    url = f"{MIRROR}/{name}"
    print(f"a descarregar {url} ...", file=sys.stderr)
    with urllib.request.urlopen(url, timeout=120) as response:
        return response.read().decode("utf-8-sig")


def load_iata_by_icao(text: str) -> dict[str, str]:
    """Só os aeroportos com designador comercial entram: é a sigla que o utilizador reconhece."""
    table: dict[str, str] = {}
    for row in csv.DictReader(io.StringIO(text)):
        icao, iata = (row.get("ICAO") or "").strip(), (row.get("IATA") or "").strip().upper()
        if icao and len(iata) == IATA_WIDTH and iata.isalpha():
            table[icao.strip()] = iata
    return table


def load_routes(text: str, iata_by_icao: dict[str, str]) -> tuple[list[tuple[str, str, str]], dict[str, int]]:
    """Rotas utilizáveis, mais a contabilidade do que se perdeu e porquê."""
    stats = {"total": 0, "sem_chave": 0, "com_escalas": 0, "sem_iata": 0}
    seen: dict[str, tuple[str, str]] = {}
    duplicates = 0

    for row in csv.DictReader(io.StringIO(text)):
        stats["total"] += 1

        key = normalize_callsign(row.get("Callsign") or "")
        if key is None:
            stats["sem_chave"] += 1
            continue

        codes = [code for code in (row.get("AirportCodes") or "").split("-") if code]
        if len(codes) != 2:
            # Rotas com escalas ficam de fora por decisão de desenho (D3): num voo LIS-SID-GRU
            # visto sobre Cabo Verde, "de onde vem" é Sal e não Lisboa. São +5,9% de registos ao
            # preço de uma ambiguidade que o utilizador não tem como detetar.
            stats["com_escalas"] += 1
            continue

        origin, destination = iata_by_icao.get(codes[0]), iata_by_icao.get(codes[1])
        if not origin or not destination:
            stats["sem_iata"] += 1
            continue

        candidate = (origin, destination)
        previous = seen.get(key)
        if previous is None:
            seen[key] = candidate
        elif previous != candidate:
            # Desduplicação determinística: com o mesmo indicativo a apontar para rotas diferentes
            # — horários que mudaram ao longo do tempo — fica sempre a menor em ordem alfabética.
            # O critério é arbitrário; o que não pode ser arbitrário é o resultado, senão duas
            # gerações dos mesmos dados produzem ficheiros diferentes.
            duplicates += 1
            seen[key] = min(previous, candidate)

    stats["duplicados"] = duplicates
    routes = [(key, origin, destination) for key, (origin, destination) in seen.items()]
    routes.sort(key=lambda item: item[0].ljust(CALLSIGN_WIDTH))
    return routes, stats


def write_table(routes: list[tuple[str, str, str]], output: Path, generated_at: int) -> None:
    header = (
        MAGIC
        + struct.pack(">H", FORMAT_VERSION)
        + struct.pack(">I", len(routes))
        + struct.pack(">q", generated_at)
        + bytes(HEADER_SIZE - len(MAGIC) - 2 - 4 - 8)
    )
    assert len(header) == HEADER_SIZE, len(header)

    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as handle:
        handle.write(header)
        for key, origin, destination in routes:
            handle.write(key.encode("ascii").ljust(CALLSIGN_WIDTH, PAD))
            handle.write(origin.encode("ascii"))
            handle.write(destination.encode("ascii"))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("app/src/main/assets/routes.bin"),
        help="ficheiro a gerar",
    )
    parser.add_argument(
        "--input-dir",
        type=Path,
        default=None,
        help="ler routes.csv e airports.csv daqui em vez de os descarregar",
    )
    parser.add_argument(
        "--generated-at",
        type=int,
        default=None,
        help="epoch a gravar no cabeçalho; por omissão, agora. Fixar torna a saída reprodutível",
    )
    args = parser.parse_args()

    iata_by_icao = load_iata_by_icao(fetch("airports.csv", args.input_dir))
    routes, stats = load_routes(fetch("routes.csv", args.input_dir), iata_by_icao)

    if len(routes) < MIN_EXPECTED_ROUTES:
        print(
            f"ERRO: apenas {len(routes)} rotas utilizáveis, abaixo do mínimo de "
            f"{MIN_EXPECTED_ROUTES}. A fonte mudou de forma?",
            file=sys.stderr,
        )
        return 1

    generated_at = args.generated_at if args.generated_at is not None else int(time.time())
    write_table(routes, args.output, generated_at)

    print(f"{args.output}: {len(routes)} rotas, {args.output.stat().st_size} bytes")
    print(f"  aeroportos com sigla IATA: {len(iata_by_icao)}")
    print(f"  registos lidos: {stats['total']}")
    print(f"  descartados — indicativo inválido: {stats['sem_chave']}")
    print(f"  descartados — com escalas: {stats['com_escalas']}")
    print(f"  descartados — sem sigla IATA nas duas pontas: {stats['sem_iata']}")
    print(f"  indicativos com rotas divergentes, resolvidos deterministicamente: {stats['duplicados']}")
    print(f"  data de geração no cabeçalho: {generated_at}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
