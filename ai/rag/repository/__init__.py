"""
The RAG's database, Postgres with pgvector, through SQLAlchemy. `database.py` connects and creates the tables,
`entities.py` maps them, and each table has its repository: a contract the rest of the RAG uses, and its Postgres
implementation (`ChunkRepository` and `ChunkRepositoryPostgres`, `EmbeddingRepository` and
`EmbeddingRepositoryPostgres`).
"""
