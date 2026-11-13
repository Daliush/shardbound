import math

import pytest

from rag.evaluation.retrieval_metrics import (CONTEXT_METRICS, RANKING_METRICS, RETRIEVAL_METRICS, BinaryNdcgAtK,
                                              ContextRecall, RecallAtK, ReciprocalRank, find_relevant_ranks,
                                              find_step_metrics)
from rag.technique import Step

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


def test_every_metric_says_what_it_does_how_and_what_it_represents():
    for metric in RETRIEVAL_METRICS:
        assert metric.description.what.strip(), metric.key
        assert metric.description.how.strip(), metric.key
        assert metric.description.represents.strip(), metric.key


def test_describes_each_metric_at_its_cutoff():
    assert "counts as much at rank 10 as at rank 1, and not at all at rank 11" in RecallAtK(10).description.represents
    assert "sit high in the first 20, not just somewhere in them" in BinaryNdcgAtK(20).description.represents
    assert "is at rank 20 gets 100% on Recall@20, but only 0.23 here" in BinaryNdcgAtK(20).description.represents
    assert "the first 7 chunks" in RecallAtK(7).description.represents, "a k the evaluation does not report"


def test_describes_every_metric_in_general_terms_not_by_what_the_arbiter_does_with_the_chunks():
    for metric in RETRIEVAL_METRICS:
        description = metric.description
        assert "prompt" not in description.what + description.how + description.represents, metric.key


def test_lists_recall_and_binary_ndcg_at_10_and_20_then_the_reciprocal_rank_each_named_by_its_key():
    keys = []
    paths = []
    names = []
    for metric in RETRIEVAL_METRICS:
        keys.append(metric.key)
        paths.append(metric.description.path)
        names.append(metric.description.name)

    assert keys == ["recall_at.10", "recall_at.20", "binary_ndcg_at.10", "binary_ndcg_at.20", "reciprocal_rank"]
    assert paths == ["mean_recall_at.10", "mean_recall_at.20", "mean_binary_ndcg_at.10", "mean_binary_ndcg_at.20",
                     "mean_reciprocal_rank"], "a group's scores hold each mean at mean_ and the key"
    assert names == ["Recall@10", "Recall@20", "Binary nDCG@10", "Binary nDCG@20", "MRR"]


def test_scores_the_share_of_the_relevant_chunks_a_context_holds_wherever_they_are():
    assert ContextRecall(None).score(RANKS) == 0.75, "11.3.3 is not in it; 11.5.4, twelfth, is"
    assert ContextRecall(None).score({"8.2": 25}) == 1.0
    assert ContextRecall(None).score({"8.2": None}) == 0.0


def test_scores_a_ranking_on_the_chunks_the_prompt_would_hold_its_recall_at_10():
    assert ContextRecall(10).score(RANKS) == 0.5, "11.5.4 is twelfth"
    for relevant_ranks in (RANKS, {"8.2": 10}, {"8.2": 11}, {"8.2": None, "8.3": 1}):
        assert ContextRecall(10).score(relevant_ranks) == RecallAtK(10).score(relevant_ranks)


def test_scores_a_ranking_with_context_recall_then_every_metric_of_its_order_and_a_context_with_three():
    keys = []
    for metric in find_step_metrics(Step.RERANK):
        keys.append(metric.key)
    context_keys = []
    for metric in find_step_metrics(Step.CONTEXT):
        context_keys.append(metric.key)

    assert find_step_metrics(Step.RETRIEVER) == RANKING_METRICS
    assert keys == ["context_recall", "recall_at.10", "recall_at.20", "binary_ndcg_at.10", "binary_ndcg_at.20",
                    "reciprocal_rank"]
    assert find_step_metrics(Step.CONTEXT) == CONTEXT_METRICS
    assert context_keys == ["context_recall", "recall_at.10", "binary_ndcg_at.10"]


def test_names_and_describes_the_context_recall_the_same_way_for_a_ranking_and_a_context():
    for context_recall in (ContextRecall(10), ContextRecall(None)):
        assert context_recall.key == "context_recall"
        assert context_recall.description.path == "mean_context_recall"
        assert context_recall.description.name == "Context recall"
        assert "For a ranking, it is Recall@10" in context_recall.description.how
        assert "It decides between pipelines" in context_recall.description.represents
    assert ContextRecall(10).description == ContextRecall(None).description
