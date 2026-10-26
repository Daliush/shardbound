"""Keeps a model's vectors up to date with the chunks."""

from rag.chunks import Chunk
from rag.repository.embedding_repository import EmbeddingRepository
from rag.retrievers.dense.embedding_models import EmbeddingModel


def embed_changed_chunks(chunks: list[Chunk], model: EmbeddingModel, repository: EmbeddingRepository) -> int:
    """
    Embeds only the chunks whose text changed since this model last embedded them: the repository keeps, with each
    vector, the hash of the text it was made from, under the model's index key. A model run another way has another
    key, so every chunk is embedded again. Returns how many were embedded.
    """
    embedded_hashes = repository.find_embedded_hashes(model.index_key)
    changed = [chunk for chunk in chunks if embedded_hashes.get(chunk.id) != chunk.hash]
    embeddings = []
    for chunk in changed:
        embeddings.append(model.embed_chunk(chunk))
    repository.save_embeddings(model.index_key, embeddings)
    return len(changed)
