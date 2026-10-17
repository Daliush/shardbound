from pathlib import Path

import pytest

from dataset import jsonl
from rag.chunks import CHUNKS_FILE, Chunk, read_chunks


def test_reads_the_chunks_the_engine_writes_with_their_names(tmp_path: Path):
    jsonl.write(tmp_path / CHUNKS_FILE, [
        {"id": "11.3.5", "kind": "rule", "name": None, "text": "[11.3 Anchor] Destroyed (5.4.3).", "refs": ["5.4.3"],
         "source": "docs/rules/11-3-anchor.md", "hash": "abc"},
        {"id": "neutral.tempest", "kind": "card", "name": "Tempest", "text": "Tempest: Neutral spell.", "refs": ["8.2"],
         "source": "content/cards/neutral/tempest.json", "hash": "def"}])

    assert read_chunks(tmp_path) == [
        Chunk("11.3.5", "rule", None, "[11.3 Anchor] Destroyed (5.4.3).", ("5.4.3",), "docs/rules/11-3-anchor.md",
              "abc"),
        Chunk("neutral.tempest", "card", "Tempest", "Tempest: Neutral spell.", ("8.2",),
              "content/cards/neutral/tempest.json", "def")]


def test_says_how_to_write_the_chunks_when_they_are_missing(tmp_path: Path):
    with pytest.raises(FileNotFoundError, match="exec:java"):
        read_chunks(tmp_path)
