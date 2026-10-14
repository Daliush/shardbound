"""The RAG's two tables, as SQLAlchemy entities: the tables are created from them."""

import numpy as np
from pgvector.sqlalchemy import Vector
from sqlalchemy import ARRAY, ForeignKey, String
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    pass


class ChunkRow(Base):
    """A chunk as engine/corpus wrote it, refs included."""

    __tablename__ = "chunks"

    id: Mapped[str] = mapped_column(primary_key=True)
    kind: Mapped[str]
    text: Mapped[str]
    refs: Mapped[list[str]] = mapped_column(ARRAY(String))
    source: Mapped[str]
    hash: Mapped[str]


class ChunkEmbeddingRow(Base):
    """
    One chunk's vector for one embedding model, with the hash of the text it was made from: when the chunk's hash
    differs, the chunk is embedded again. 191 rows per model need no vector index: pgvector compares them all.
    """

    __tablename__ = "chunk_embeddings"

    chunk_id: Mapped[str] = mapped_column(ForeignKey(ChunkRow.id, ondelete="CASCADE"), primary_key=True)
    model: Mapped[str] = mapped_column(primary_key=True)
    hash: Mapped[str]
    embedding: Mapped[np.ndarray] = mapped_column(Vector())
