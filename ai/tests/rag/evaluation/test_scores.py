from rag.evaluation.scores import QuestionResult, average_scores


def build_result(candidate_recall: float, candidate_count: int) -> QuestionResult:
    return QuestionResult(question_id="anchor", category="interaction", top_chunk_ids=[], recall_at={10: 0.0, 20: 0.0},
                          reciprocal_rank=0.0, candidate_recall=candidate_recall, candidate_count=candidate_count)


def test_averages_the_candidate_recall_and_count_with_every_question_weighing_the_same():
    scores = average_scores([build_result(0.5, 20), build_result(1.0, 41)])

    assert scores.mean_candidate_recall == 0.75
    assert scores.mean_candidate_count == 30.5
