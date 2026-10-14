import numpy as np
import pytest

from rag.chunks import Chunk
from rag.repository.embedding_repository import EmbeddingRepository
from rag.retrievers.dense.chunk_embedding import ChunkEmbedding
from rag.retrievers.dense.embedding_models import EmbeddingModel

VOCABULARY = ["anchor", "destroy", "heal", "deck", "unit"]


class FakeEmbeddingModel(EmbeddingModel):
    """A vector of word counts over a tiny vocabulary: no download, and close texts get close vectors."""

    name = "fake"

    def __init__(self) -> None:
        self.embedded_texts: list[str] = []

    def embed_chunk(self, chunk: Chunk) -> ChunkEmbedding:
        self.embedded_texts.append(chunk.text)
        return ChunkEmbedding(chunk, self._count_words(chunk.text))

    def embed_question(self, question: str) -> np.ndarray:
        return self._count_words(question)

    @staticmethod
    def _count_words(text: str) -> np.ndarray:
        words = text.lower().split()
        return np.array([sum(word.startswith(term) for word in words) for term in VOCABULARY], dtype=float) + 0.01


class EmbeddingRepositoryInMemory(EmbeddingRepository):
    """What EmbeddingRepositoryPostgres keeps in its table, in a dictionary; the cosine computed with numpy."""

    def __init__(self) -> None:
        self.embeddings: dict[tuple[str, str], ChunkEmbedding] = {}

    def find_embedded_hashes(self, model_name: str) -> dict[str, str]:
        hashes = {}
        for (chunk_id, owner), embedding in self.embeddings.items():
            if owner == model_name:
                hashes[chunk_id] = embedding.chunk.hash
        return hashes

    def save_embeddings(self, model_name: str, embeddings: list[ChunkEmbedding]) -> None:
        for embedding in embeddings:
            self.embeddings[(embedding.chunk.id, model_name)] = embedding

    def rank_chunk_ids(self, model_name: str, question_vector: np.ndarray) -> list[str]:
        cosine_by_chunk_id = {}
        for (chunk_id, owner), embedding in self.embeddings.items():
            if owner == model_name:
                vector = embedding.vector
                cosine_by_chunk_id[chunk_id] = float(
                    vector @ question_vector / (np.linalg.norm(vector) * np.linalg.norm(question_vector)))
        return sorted(cosine_by_chunk_id, key=cosine_by_chunk_id.get, reverse=True)


@pytest.fixture
def fake_model() -> FakeEmbeddingModel:
    return FakeEmbeddingModel()


@pytest.fixture
def embedding_repository() -> EmbeddingRepositoryInMemory:
    return EmbeddingRepositoryInMemory()


@pytest.fixture
def rule_chunks() -> list[Chunk]:
    return [
        Chunk("11.3.2", "rule", "[11.3 Anchor] While protected, an anchor unit cannot be destroyed.", (), "r", "h1"),
        Chunk("8.4", "rule", "[8. Effects] Heal X: restores X defense to a unit.", (), "r", "h2"),
        Chunk("2.1", "rule", "[2. Deck building] A deck contains exactly 30 cards.", (), "r", "h3"),
    ]
