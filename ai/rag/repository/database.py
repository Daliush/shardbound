"""The connection to the RAG's database."""

import os

from sqlalchemy import Engine, create_engine, text

from rag.repository.entities import Base

DEFAULT_DATABASE_URL = "postgresql+psycopg://shardbound:shardbound@localhost:5432/shardbound"
"""The `db` service of compose.yaml, a local database only. RAG_DATABASE_URL points elsewhere."""


def connect_database(database_url: str | None = None) -> Engine:
    """The database of `database_url`, RAG_DATABASE_URL or the local service, its pgvector extension and its tables
    created if missing."""
    engine = create_engine(database_url or os.environ.get("RAG_DATABASE_URL", DEFAULT_DATABASE_URL))
    with engine.begin() as connection:
        connection.execute(text("CREATE EXTENSION IF NOT EXISTS vector"))
    Base.metadata.create_all(engine)
    return engine
