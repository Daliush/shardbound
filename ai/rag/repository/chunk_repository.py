"""The chunks table, the RAG's only source of chunks: once indexed, nothing reads the engine's file again."""

from abc import ABC, abstractmethod

from sqlalchemy import Engine, delete, select
from sqlalchemy.orm import Session

from rag.chunks import Chunk
from rag.repository.entities import ChunkRow


class ChunkRepository(ABC):

    @abstractmethod
    def save_chunks(self, chunks: list[Chunk]) -> None:
        """Inserts or updates every chunk, and deletes the ones the corpus no longer has, with their vectors."""

    @abstractmethod
    def find_all_chunks(self) -> list[Chunk]:
        """Every chunk, by id."""


class ChunkRepositoryPostgres(ChunkRepository):

    def __init__(self, engine: Engine) -> None:
        self._engine = engine

    def save_chunks(self, chunks: list[Chunk]) -> None:
        with Session(self._engine) as session, session.begin():
            for chunk in chunks:
                session.merge(ChunkRow(id=chunk.id, kind=chunk.kind, name=chunk.name, text=chunk.text,
                                       refs=list(chunk.refs), source=chunk.source, hash=chunk.hash))
            session.execute(delete(ChunkRow).where(ChunkRow.id.not_in([chunk.id for chunk in chunks])))

    def find_all_chunks(self) -> list[Chunk]:
        with Session(self._engine) as session:
            chunks = []
            for row in session.scalars(select(ChunkRow).order_by(ChunkRow.id)):
                chunks.append(Chunk(row.id, row.kind, row.name, row.text, tuple(row.refs), row.source, row.hash))
            return chunks
