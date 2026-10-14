"""The chunks table: what every part of the RAG reads, the dense index today, the refs expansion next."""

from abc import ABC, abstractmethod

from sqlalchemy import Engine, delete
from sqlalchemy.orm import Session

from rag.chunks import Chunk
from rag.repository.entities import ChunkRow


class ChunkRepository(ABC):

    @abstractmethod
    def save_chunks(self, chunks: list[Chunk]) -> None:
        """Inserts or updates every chunk, and deletes the ones the corpus no longer has, with their vectors."""


class ChunkRepositoryPostgres(ChunkRepository):

    def __init__(self, engine: Engine) -> None:
        self._engine = engine

    def save_chunks(self, chunks: list[Chunk]) -> None:
        with Session(self._engine) as session, session.begin():
            for chunk in chunks:
                session.merge(ChunkRow(id=chunk.id, kind=chunk.kind, text=chunk.text, refs=list(chunk.refs),
                                       source=chunk.source, hash=chunk.hash))
            session.execute(delete(ChunkRow).where(ChunkRow.id.not_in([chunk.id for chunk in chunks])))
