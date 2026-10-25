import sys
from types import SimpleNamespace

import pytest


class RecordingCrossEncoder:
    """Stands for sentence-transformers' CrossEncoder: keeps what a reranker built it with, downloads nothing."""

    def __init__(self, hugging_face_id: str, **settings) -> None:
        self.hugging_face_id = hugging_face_id
        self.settings = settings


@pytest.fixture
def sentence_transformers(monkeypatch) -> None:
    """A fake `sentence_transformers` module, which `_load_cross_encoder` imports instead of the real one (and torch)."""
    monkeypatch.setitem(sys.modules, "sentence_transformers", SimpleNamespace(CrossEncoder=RecordingCrossEncoder))
