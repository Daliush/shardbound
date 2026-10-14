"""The chunk_embeddings table: one vector per chunk and embedding model, which pgvector ranks by cosine."""

from abc import ABC, abstractmethod

import numpy as np
from sqlalchemy import Engine, select
from sqlalchemy.orm import Session

from rag.repository.entities import ChunkEmbeddingRow
from rag.retrievers.dense.chunk_embedding import ChunkEmbedding


class EmbeddingRepository(ABC):

    @abstractmethod
    def find_embedded_hashes(self, model_name: str) -> dict[str, str]:
        """Chunk id → the hash of the text its vector was made from, for this model."""

    @abstractmethod
    def save_embeddings(self, model_name: str, embeddings: list[ChunkEmbedding]) -> None:
        """Inserts or replaces this model's vector of each chunk, with the hash of the text it was made from."""

    @abstractmethod
    def rank_chunk_ids(self, model_name: str, question_vector: np.ndarray) -> list[str]:
        """Every chunk this model has a vector for, the closest to the question first."""


class EmbeddingRepositoryPostgres(EmbeddingRepository):

    def __init__(self, engine: Engine) -> None:
        self._engine = engine

    def find_embedded_hashes(self, model_name: str) -> dict[str, str]:
        with Session(self._engine) as session:
            rows = session.execute(select(ChunkEmbeddingRow.chunk_id, ChunkEmbeddingRow.hash)
                                   .where(ChunkEmbeddingRow.model == model_name))
            return {chunk_id: hash_ for chunk_id, hash_ in rows}

    def save_embeddings(self, model_name: str, embeddings: list[ChunkEmbedding]) -> None:
        with Session(self._engine) as session, session.begin():
            for embedding in embeddings:
                session.merge(ChunkEmbeddingRow(chunk_id=embedding.chunk.id, model=model_name,
                                                hash=embedding.chunk.hash, embedding=embedding.vector))

    def rank_chunk_ids(self, model_name: str, question_vector: np.ndarray) -> list[str]:
        """Postgres computes the cosine distance (1 - cosine) of every vector to the question's, then sorts."""
        with Session(self._engine) as session:
            return list(session.scalars(
                select(ChunkEmbeddingRow.chunk_id)
                .where(ChunkEmbeddingRow.model == model_name)
                .order_by(ChunkEmbeddingRow.embedding.cosine_distance(question_vector))))
