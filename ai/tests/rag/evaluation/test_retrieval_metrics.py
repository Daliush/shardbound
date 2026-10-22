import math

import pytest

from rag.evaluation.retrieval_metrics import (RETRIEVAL_METRICS, BinaryNdcgAtK, RecallAtK, ReciprocalRank,
                                              find_relevant_ranks)

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
    assert RecallAtK(5).score(RANKS) == 0.5
    assert RecallAtK(11).score(RANKS) == 0.5
    assert RecallAtK(12).score(RANKS) == 0.75


def test_binary_ndcg_at_k_of_a_single_relevant_chunk_is_the_discount_of_its_rank():
    assert BinaryNdcgAtK(10).score({"8.3": 1}) == 1.0
    assert BinaryNdcgAtK(10).score({"8.3": 3}) == 0.5, "rule/8.3 after the reranker"
    assert BinaryNdcgAtK(10).score({"2.1": 10}) == pytest.approx(0.289, abs=0.001), "a deck question, last of 10"
    assert BinaryNdcgAtK(10).score({"8.3": 17}) == 0.0


def test_binary_ndcg_at_k_divides_by_the_gains_of_every_relevant_chunk_ranked_first():
    nine_relevant = {"9.11": 1, "1.2": 6, "9.8": 10, "8.1": 12, "8.2": 24, "9.3": 27, "6.3": 28, "1.6": None,
                     "3.5": None}
    gains = 1 + 1 / math.log2(7) + 1 / math.log2(11)
    best_gains = 0.0
    for rank in range(1, 10):
        best_gains += 1 / math.log2(rank + 1)

    assert BinaryNdcgAtK(10).score(nine_relevant) == pytest.approx(gains / best_gains)
    assert BinaryNdcgAtK(10).score(nine_relevant) == pytest.approx(0.387, abs=0.001), "the 2-HP Tempest question"


def test_binary_ndcg_at_k_is_one_when_the_first_k_are_all_relevant_even_with_more_relevant_chunks_left():
    twelve_ranked_first = {}
    for rank in range(1, 13):
        twelve_ranked_first[f"8.{rank}"] = rank

    assert BinaryNdcgAtK(10).score(twelve_ranked_first) == 1.0
    assert RecallAtK(10).score(twelve_ranked_first) == 10 / 12


def test_binary_ndcg_at_k_of_a_perfect_ranking_is_exactly_one_whatever_the_order_of_the_ids():
    ranked_first_in_another_order = {"1.1": 1, "1.2": 2, "1.3": 6, "1.4": 3, "1.5": 4, "1.6": 5}

    assert BinaryNdcgAtK(10).score(ranked_first_in_another_order) == 1.0, "0.9999999999999999 added in id order"


def test_reciprocal_rank_reads_the_rank_of_the_first_relevant_chunk_only():
    assert ReciprocalRank().score(RANKS) == 1.0
    assert ReciprocalRank().score({"11.3.2": 3, "11.5.4": 7}) == 1 / 3


def test_reciprocal_rank_is_zero_when_no_relevant_chunk_is_ranked():
    assert ReciprocalRank().score({"11.3.2": None, "11.5.4": None}) == 0.0


def test_lists_recall_and_binary_ndcg_at_10_and_20_then_the_reciprocal_rank_each_named_by_its_key():
    keys = []
    names = []
    for metric in RETRIEVAL_METRICS:
        keys.append(metric.description.key)
        names.append(metric.description.name)

    assert keys == ["recall_at.10", "recall_at.20", "binary_ndcg_at.10", "binary_ndcg_at.20", "reciprocal_rank"]
    assert names == ["Recall@10", "Recall@20", "Binary nDCG@10", "Binary nDCG@20", "MRR"]
