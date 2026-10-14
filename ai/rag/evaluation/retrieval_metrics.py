"""The retrieval metrics of one question: how well a ranking holds the chunks it needs."""


def recall_at_k(ranked_ids: list[str], relevant_ids: frozenset[str], k: int) -> float:
    """The share of the relevant chunks found among the first `k` ranked: 2 of 4 gives 0.5."""
    return len(relevant_ids & set(ranked_ids[:k])) / len(relevant_ids)


def reciprocal_rank(ranked_ids: list[str], relevant_ids: frozenset[str]) -> float:
    """1 / the rank of the first relevant chunk (first: 1.0, third: 0.33), 0.0 when none is ranked."""
    for rank, chunk_id in enumerate(ranked_ids, start=1):
        if chunk_id in relevant_ids:
            return 1 / rank
    return 0.0
