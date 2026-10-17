"""Dense retrieval (design doc §5.2): the chunks closest in meaning to the question."""

from rag.repository.embedding_repository import EmbeddingRepository
from rag.retrievers import Retriever
from rag.retrievers.dense.embedding_models import EmbeddingModel


class DenseRetriever(Retriever):
    """Embeds the question with the model that embedded the chunks; the repository ranks every chunk by cosine."""

    def __init__(self, model: EmbeddingModel, repository: EmbeddingRepository) -> None:
        if not repository.find_embedded_hashes(model.name):
            raise LookupError(f"No vectors for {model.name}: build the index with `uv run python -m rag index`")
        self._model = model
        self._repository = repository

    @property
    def description(self) -> str:
        return f"dense retrieval ({self._model.name} embeddings, ranked by cosine)"

    def rank_chunks(self, question: str) -> list[str]:
        return self._repository.rank_chunk_ids(self._model.name, self._model.embed_question(question))
