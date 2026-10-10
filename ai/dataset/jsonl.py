"""JSON Lines, one object per line, as `engine/dataset` writes them."""

import json
from pathlib import Path


def read(path: Path) -> list[dict]:
    with path.open(encoding="utf-8") as lines:
        return [json.loads(line) for line in lines if line.strip()]


def write(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as out:
        for row in rows:
            out.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
