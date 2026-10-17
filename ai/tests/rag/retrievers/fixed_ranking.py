from rag.retrievers import Retriever


class FixedRanking(Retriever):
    """Ranks the same chunks whatever the question: the retriever another one wraps, in tests."""

    def __init__(self, ranked_chunk_ids: list[str]) -> None:
        self._ranked_chunk_ids = ranked_chunk_ids

    def rank_chunks(self, question: str) -> list[str]:
        return list(self._ranked_chunk_ids)
