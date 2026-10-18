"""The first reranker (design doc §5.2): MiniLM-L6, a small cross-encoder trained on web search queries."""

from functools import cached_property

from rag.chunks import Chunk
from rag.rerankers import Reranker


class MiniLMReranker(Reranker):
    """
    Reads the question together with each candidate's text, scores how well the text answers it, and orders the
    candidates by that score. The model, `cross-encoder/ms-marco-MiniLM-L6-v2` (22M parameters), runs on CPU with
    sentence-transformers; it is downloaded from Hugging Face on first use, then read from its cache.
    """

    HUGGING_FACE_ID = "cross-encoder/ms-marco-MiniLM-L6-v2"

    def __init__(self, name: str, chunks: list[Chunk]) -> None:
        super().__init__(name)
        self._text_by_chunk_id: dict[str, str] = {}
        for chunk in chunks:
            self._text_by_chunk_id[chunk.id] = chunk.text

    @property
    def description(self) -> str:
        return "reranked by MiniLM-L6, a cross-encoder that reads the question with each candidate"

    def rerank_chunks(self, question: str, chunk_ids: list[str]) -> list[str]:
        texts = []
        for chunk_id in chunk_ids:
            texts.append(self._text_by_chunk_id[chunk_id])
        reranked_ids = []
        # Each hit names its candidate by its position in `texts`, the highest score first; on a tie, the retriever's
        # order stays.
        for hit in self._model.rank(question, texts):
            reranked_ids.append(chunk_ids[hit["corpus_id"]])
        return reranked_ids

    @cached_property
    def _model(self):
        """Loaded on the first question to rerank, not before: building the reranker costs nothing."""
        # Imported here, not at the top: torch takes seconds to load, and BM25 runs and the tests never need it.
        from sentence_transformers import CrossEncoder

        return CrossEncoder(self.HUGGING_FACE_ID, device="cpu")
