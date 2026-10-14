"""The index's flow: the chunks into the database, then each local embedding model's vectors of the changed ones."""

from pathlib import Path

from rag.chunks import read_chunks
from rag.repository.chunk_repository import ChunkRepositoryPostgres
from rag.repository.database import connect_database
from rag.repository.embedding_repository import EmbeddingRepositoryPostgres
from rag.retrievers.dense.chunk_embedder import embed_changed_chunks
from rag.retrievers.dense.embedding_models import LOCAL_EMBEDDING_MODELS, LocalEmbeddingModel


def build_index(root: Path) -> dict[str, int]:
    """Indexes the chunks of the repository at `root`. Returns how many chunks each model embedded."""
    chunks = read_chunks(root)
    database = connect_database()
    ChunkRepositoryPostgres(database).save_chunks(chunks)
    embedding_repository = EmbeddingRepositoryPostgres(database)
    embedded_by_model = {}
    for model_name in LOCAL_EMBEDDING_MODELS:
        embedded_by_model[model_name] = embed_changed_chunks(chunks, LocalEmbeddingModel(model_name),
                                                             embedding_repository)
    return embedded_by_model
