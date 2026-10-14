"""The embedding models the dense retriever can use, run locally with sentence-transformers."""

from abc import ABC, abstractmethod
from functools import cached_property

import numpy as np

from rag.chunks import Chunk
from rag.retrievers.dense.chunk_embedding import ChunkEmbedding

LOCAL_EMBEDDING_MODELS: dict[str, str] = {
    "qwen3-0.6b": "Qwen/Qwen3-Embedding-0.6B",
}
"""Our name for each model, then its Hugging Face id. A model added here becomes the `dense-<name>` retriever."""


class EmbeddingModel(ABC):
    """Turns texts into vectors. A question's vector is comparable with the chunks' only if one model made both."""

    name: str

    @abstractmethod
    def embed_chunk(self, chunk: Chunk) -> ChunkEmbedding:
        """The chunk with the vector of its text."""

    @abstractmethod
    def embed_question(self, question: str) -> np.ndarray:
        """The question's vector."""


class LocalEmbeddingModel(EmbeddingModel):
    """
    One model of `LOCAL_EMBEDDING_MODELS`, by our name for it: `LocalEmbeddingModel("qwen3-0.6b")`. Downloaded from
    Hugging Face on first use, then cached. Its own configuration adds the marker it was trained with: Qwen3 puts an
    instruction before the question and nothing before a chunk.
    """

    def __init__(self, name: str) -> None:
        self.name = name
        self.hugging_face_id = LOCAL_EMBEDDING_MODELS[name]

    @cached_property
    def _model(self):
        """Loaded on the first text to embed, not before: an index run with nothing changed never loads it."""
        # Imported here, not at the top: torch takes seconds to load, and BM25 runs and the tests never need it.
        from sentence_transformers import SentenceTransformer

        return SentenceTransformer(self.hugging_face_id, device="cpu")

    def embed_chunk(self, chunk: Chunk) -> ChunkEmbedding:
        return ChunkEmbedding(chunk, self._model.encode_document(chunk.text))

    def embed_question(self, question: str) -> np.ndarray:
        return self._model.encode_query([question])[0]
