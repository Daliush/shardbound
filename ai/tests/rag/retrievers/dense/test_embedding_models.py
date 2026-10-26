import sys
from types import SimpleNamespace

from rag.retrievers.dense.embedding_models import LocalEmbeddingModel


class RecordingSentenceTransformer:
    """Stands for sentence-transformers' SentenceTransformer: keeps what the model was built with, downloads nothing."""

    def __init__(self, hugging_face_id: str, **settings) -> None:
        self.hugging_face_id = hugging_face_id
        self.settings = settings


def test_loads_its_model_in_float32_on_the_device_rag_device_names(monkeypatch):
    monkeypatch.setitem(sys.modules, "sentence_transformers",
                        SimpleNamespace(SentenceTransformer=RecordingSentenceTransformer))
    monkeypatch.setenv("RAG_DEVICE", "cuda")

    model = LocalEmbeddingModel("qwen3-0.6b")._model

    assert model.hugging_face_id == "Qwen/Qwen3-Embedding-0.6B"
    assert model.settings == {"device": "cuda", "model_kwargs": {"dtype": "float32"}}


def test_keeps_its_vectors_under_its_name_and_format():
    assert LocalEmbeddingModel("qwen3-0.6b").index_key == "qwen3-0.6b/float32"
