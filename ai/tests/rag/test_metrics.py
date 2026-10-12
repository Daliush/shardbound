from rag.evaluation.metrics import recall_at_k, reciprocal_rank

RELEVANT = frozenset({"11.3.2", "11.3.3", "11.5.4", "neutral.tempest"})
"""The example we worked through: 11.3.2 first, Tempest third, 11.5.4 twelfth, 11.3.3 not ranked."""
RANKED = ["11.3.2", "11.3.5", "neutral.tempest", "8.2.1", "11.5.1", "7.1", "7.2", "7.3", "7.4", "7.5", "7.6",
          "11.5.4", "7.7", "7.8", "7.9", "7.10", "8.1", "8.2", "8.3", "8.4"]


def test_recall_at_k_is_the_share_of_the_relevant_chunks_among_the_first_k():
    assert recall_at_k(RANKED, RELEVANT, 5) == 0.5
    assert recall_at_k(RANKED, RELEVANT, 20) == 0.75


def test_reciprocal_rank_reads_the_rank_of_the_first_relevant_chunk_only():
    assert reciprocal_rank(RANKED, RELEVANT) == 1.0
    assert reciprocal_rank(["7.1", "7.2", "11.3.2"], RELEVANT) == 1 / 3


def test_reciprocal_rank_is_zero_when_no_relevant_chunk_is_ranked():
    assert reciprocal_rank(["7.1", "7.2"], RELEVANT) == 0.0
