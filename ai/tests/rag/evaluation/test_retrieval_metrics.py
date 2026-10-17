from rag.evaluation.retrieval_metrics import find_relevant_ranks, recall_at_k, reciprocal_rank

RELEVANT = frozenset({"11.3.2", "11.3.3", "11.5.4", "neutral.tempest"})
"""The example we worked through: 11.3.2 first, Tempest third, 11.5.4 twelfth, 11.3.3 not ranked."""
RANKED = ["11.3.2", "11.3.5", "neutral.tempest", "8.2.1", "11.5.1", "7.1", "7.2", "7.3", "7.4", "7.5", "7.6",
          "11.5.4", "7.7", "7.8", "7.9", "7.10", "8.1", "8.2", "8.3", "8.4"]
RANKS = {"11.3.2": 1, "11.3.3": None, "11.5.4": 12, "neutral.tempest": 3}


def test_finds_the_rank_of_each_relevant_chunk_by_id_and_none_when_it_is_left_out():
    found = find_relevant_ranks(RANKED, RELEVANT)

    assert found == RANKS
    assert list(found) == ["11.3.2", "11.3.3", "11.5.4", "neutral.tempest"]


def test_keeps_the_first_rank_of_a_chunk_ranked_twice():
    assert find_relevant_ranks(["11.3.2", "7.1", "11.3.2"], frozenset({"11.3.2"})) == {"11.3.2": 1}


def test_recall_at_k_is_the_share_of_the_relevant_chunks_among_the_first_k():
    assert recall_at_k(RANKS, 5) == 0.5
    assert recall_at_k(RANKS, 11) == 0.5
    assert recall_at_k(RANKS, 12) == 0.75


def test_reciprocal_rank_reads_the_rank_of_the_first_relevant_chunk_only():
    assert reciprocal_rank(RANKS) == 1.0
    assert reciprocal_rank({"11.3.2": 3, "11.5.4": 7}) == 1 / 3


def test_reciprocal_rank_is_zero_when_no_relevant_chunk_is_ranked():
    assert reciprocal_rank({"11.3.2": None, "11.5.4": None}) == 0.0
