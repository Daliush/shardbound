"""Reciprocal Rank Fusion (design doc §5.2): several rankings merged by rank, since a BM25 score and a cosine cannot
be compared."""

from rag.retrievers import Retriever


class RrfRetriever(Retriever):
    """
    Asks each retriever for its ranking; a chunk scores 1 / (k + its rank) in each ranking it is in, summed over them.
    A chunk that a retriever leaves out, as BM25 leaves out the chunks sharing no word with the question, scores
    nothing from that one. On a tie, the chunk the first rankings met first comes first.
    """

    def __init__(self, retrievers: list[Retriever], k: int = 60) -> None:
        self._retrievers = list(retrievers)
        self._k = k
        """Damps the top ranks: with 60, the first rank scores 1/61 and the tenth 1/70, close enough that being
        ranked by both retrievers beats being first in one."""

    def rank_chunks(self, question: str) -> list[str]:
        score_by_chunk_id: dict[str, float] = {}
        for retriever in self._retrievers:
            for rank, chunk_id in enumerate(retriever.rank_chunks(question), start=1):
                score_by_chunk_id[chunk_id] = score_by_chunk_id.get(chunk_id, 0) + 1 / (self._k + rank)
        return sorted(score_by_chunk_id, key=score_by_chunk_id.get, reverse=True)
