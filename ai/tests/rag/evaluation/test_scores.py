from rag.evaluation.scores import QuestionResult, average_scores


def build_result(relevant_ranks: dict[str, int | None], candidate_recall: float,
                 candidate_count: int) -> QuestionResult:
    """Only the fields `average_scores` reads in these tests are filled in: the others are left at 0."""
    return QuestionResult(question_id="anchor", category="interaction", top_chunk_ids=[], relevant_ranks=relevant_ranks,
                          recall_at={10: 0.0, 20: 0.0}, reciprocal_rank=0.0, candidate_recall=candidate_recall,
                          candidate_count=candidate_count)


def test_averages_the_candidate_recall_and_count_with_every_question_weighing_the_same():
    scores = average_scores([build_result({"8.2": 1, "8.3": None}, 0.5, 20), build_result({"8.2": 7}, 1.0, 41)])

    assert scores.mean_candidate_recall == 0.75
    assert scores.mean_candidate_count == 30.5


def test_averages_recall_at_every_k_up_to_the_longest_list_each_question_keeping_its_last_recall():
    two_of_two_by_the_third = build_result({"8.2": 1, "8.3": 3}, 1.0, 3)
    one_by_the_second_of_two = build_result({"9.8": 2}, 1.0, 2)

    scores = average_scores([two_of_two_by_the_third, one_by_the_second_of_two])

    assert scores.mean_recall_curve == [(0.5 + 0.0) / 2, (0.5 + 1.0) / 2, (1.0 + 1.0) / 2]
