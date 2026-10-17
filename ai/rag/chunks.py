"""
A chunk, and the file `engine/corpus` writes into `ai/.corpus/chunks.jsonl`, one per rule, vocabulary term and
card. The file only hands the chunks from the engine to the index: everything else reads them from the database.
"""

from dataclasses import dataclass
from pathlib import Path

from dataset import jsonl

CHUNKS_FILE = Path("ai") / ".corpus" / "chunks.jsonl"
GENERATE_COMMAND = "./mvnw -q -pl corpus -am compile exec:java"


@dataclass(frozen=True)
class Chunk:
    """
    Only `text` is embedded, indexed and shown to the model. `name` is what a card or a term is called, by which the
    RAG finds the cards a question names, and None for a rule, found by its text. `refs` are the chunks it relies on.
    """

    id: str
    kind: str
    name: str | None
    text: str
    refs: tuple[str, ...]
    source: str
    hash: str


def read_chunks(root: Path) -> list[Chunk]:
    """The chunks of the repository at `root`, in the order the engine wrote them."""
    file = root / CHUNKS_FILE
    if not file.is_file():
        raise FileNotFoundError(f"No {CHUNKS_FILE}: write it from engine/ with `{GENERATE_COMMAND}`")
    chunks = []
    for row in jsonl.read(file):
        chunks.append(Chunk(row["id"], row["kind"], row["name"], row["text"], tuple(row["refs"]), row["source"],
                            row["hash"]))
    return chunks
