from rag.retrievers import Retriever


class FixedRanking(Retriever):
    """Ranks the same chunks whatever the question: the retriever another one wraps, in tests."""

    def __init__(self, ranked_chunk_ids: list[str], description: str = "a fixed ranking", name: str = "fixed") -> None:
        super().__init__(name)
        self._ranked_chunk_ids = ranked_chunk_ids
        self._description = description

    @property
    def description(self) -> str:
        return self._description

    def rank_chunks(self, question: str) -> list[str]:
        return list(self._ranked_chunk_ids)
