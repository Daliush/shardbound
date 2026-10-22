from rag.evaluation.retrieval_metrics import RETRIEVAL_METRICS
from rag.evaluation.scores import QuestionResult, score_group


def build_result(relevant_ranks: dict[str, int | None], candidate_count: int, seconds: float = 0.0,
                 metric_value: float = 0.0) -> QuestionResult:
    """Only the fields `score_group` reads in these tests are filled in: every metric has `metric_value`."""
    metric_values = {}
    for metric in RETRIEVAL_METRICS:
        metric_values[metric.description.key] = metric_value
    return QuestionResult(question_id="anchor", category="interaction", top_chunk_ids=[], relevant_ranks=relevant_ranks,
                          metric_values=metric_values, candidate_count=candidate_count, seconds=seconds)


def test_averages_each_metric_over_the_questions():
    first = build_result({"8.2": 1}, 20, metric_value=1.0)
    third = build_result({"8.2": 3}, 20, metric_value=0.5)

    scores = score_group([first, third])

    assert scores.metric_means["binary_ndcg_at.10"] == 0.75
    assert len(scores.metric_means) == len(RETRIEVAL_METRICS)


def test_averages_the_number_of_candidates():
    scores = score_group([build_result({"8.2": 1}, 20), build_result({"8.2": 1}, 41)])

    assert scores.mean_candidate_count == 30.5


def test_averages_recall_at_every_k_up_to_the_longest_list_each_question_keeping_its_last_recall():
    two_of_two_by_the_third = build_result({"8.2": 1, "8.3": 3}, 3)
    one_by_the_second_of_two = build_result({"9.8": 2}, 2)

    scores = score_group([two_of_two_by_the_third, one_by_the_second_of_two])

    assert scores.mean_recall_curve == [(0.5 + 0.0) / 2, (0.5 + 1.0) / 2, (1.0 + 1.0) / 2]


def test_ends_the_recall_curve_at_the_share_of_the_relevant_chunks_among_every_candidate():
    half_left_out = build_result({"8.2": 1, "8.3": None}, 20)

    assert score_group([half_left_out]).mean_recall_curve[-1] == 0.5


def test_reads_the_latency_percentiles_from_the_time_each_question_took():
    results = []
    for seconds in [0.4, 0.1, 0.3, 0.2]:
        results.append(build_result({"8.2": 1}, 20, seconds))

    scores = score_group(results)

    assert scores.latency_p50_seconds == 0.2, "half of the questions took 0.2 s or less"
    assert scores.latency_p95_seconds == 0.4
