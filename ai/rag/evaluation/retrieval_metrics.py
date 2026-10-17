"""
The retrieval metrics of one question: how well a ranking holds the chunks it needs. Each is read from the ranks of
the question's relevant chunks in the ranking, which `find_relevant_ranks` finds once.
"""


def find_relevant_ranks(ranked_ids: list[str], relevant_ids: frozenset[str]) -> dict[str, int | None]:
    """Each relevant chunk's rank in the ranking, 1 for the first and None when the ranking leaves it out, by id. A
    chunk ranked twice keeps its first rank."""
    relevant_ranks: dict[str, int | None] = dict.fromkeys(sorted(relevant_ids))
    for rank, chunk_id in enumerate(ranked_ids, start=1):
        if chunk_id in relevant_ranks and relevant_ranks[chunk_id] is None:
            relevant_ranks[chunk_id] = rank
    return relevant_ranks


def recall_at_k(relevant_ranks: dict[str, int | None], k: int) -> float:
    """The share of the relevant chunks ranked among the first `k`: 2 of 4 gives 0.5."""
    found = 0
    for rank in relevant_ranks.values():
        if rank is not None and rank <= k:
            found += 1
    return found / len(relevant_ranks)


def reciprocal_rank(relevant_ranks: dict[str, int | None]) -> float:
    """1 / the rank of the first relevant chunk (first: 1.0, third: 0.33), 0.0 when none is ranked."""
    first_rank = None
    for rank in relevant_ranks.values():
        if rank is not None and (first_rank is None or rank < first_rank):
            first_rank = rank
    return 1 / first_rank if first_rank is not None else 0.0
