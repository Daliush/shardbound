from rag.evaluation.evaluation_service import evaluate
from rag.evaluation.questions import Question
from rag.retrievers import Retriever

QUESTIONS = [
    Question("rule/2.2", "simple-rule", "Two factions in one deck?", True, frozenset({"2.2"})),
    Question("anchor", "interaction", "Tempest on my Root Sentinel?", True, frozenset({"11.3.2", "11.3.3"})),
    Question("rampage", "unanswerable", "What does Rampage do?", False, frozenset()),
]
RANKINGS = {
    "Two factions in one deck?": ["2.1", "2.2"],
    "Tempest on my Root Sentinel?": ["11.3.2"] + [f"7.{n}" for n in range(1, 20)] + ["11.3.3"],
}


class FixedRetriever(Retriever):
    def __init__(self) -> None:
        self.asked: list[str] = []

    @property
    def description(self) -> str:
        return "fixed rankings"

    def rank_chunks(self, question: str) -> list[str]:
        self.asked.append(question)
        return RANKINGS[question]


def test_ranks_each_answerable_question_once_and_no_unanswerable_one():
    retriever = FixedRetriever()

    evaluate(retriever, QUESTIONS)

    assert retriever.asked == ["Two factions in one deck?", "Tempest on my Root Sentinel?"]


def test_averages_each_metric_over_the_answerable_questions_then_by_category():
    report = evaluate(FixedRetriever(), QUESTIONS)

    assert report.overall.question_count == 2
    assert report.overall.mean_recall_at == {10: (1.0 + 0.5) / 2, 20: (1.0 + 0.5) / 2}
    assert report.overall.mean_reciprocal_rank == (0.5 + 1.0) / 2
    assert report.overall.mean_candidate_count == (2 + 21) / 2
    assert list(report.by_category) == ["interaction", "simple-rule"]
    assert report.by_category["simple-rule"].mean_reciprocal_rank == 0.5
    assert report.by_category["interaction"].mean_recall_at[20] == 0.5


def test_keeps_each_question_result_with_its_first_twenty_chunks():
    report = evaluate(FixedRetriever(), QUESTIONS)

    [deck_question, anchor_question] = report.questions
    assert deck_question.question_id == "rule/2.2"
    assert deck_question.top_chunk_ids == ["2.1", "2.2"]
    assert deck_question.reciprocal_rank == 0.5
    assert len(anchor_question.top_chunk_ids) == 20
    assert anchor_question.recall_at == {10: 0.5, 20: 0.5}


def test_finds_among_all_the_candidates_a_relevant_chunk_ranked_past_twenty():
    [_, anchor_question] = evaluate(FixedRetriever(), QUESTIONS).questions

    assert anchor_question.relevant_ranks == {"11.3.2": 1, "11.3.3": 21}
    assert anchor_question.candidate_count == 21


def test_averages_a_recall_curve_that_agrees_with_recall_at_k_and_counts_a_chunk_past_the_twentieth():
    overall = evaluate(FixedRetriever(), QUESTIONS).overall

    assert len(overall.mean_recall_curve) == 21
    assert overall.mean_recall_curve[9] == overall.mean_recall_at[10]
    assert overall.mean_recall_curve[19] == overall.mean_recall_at[20]
    assert overall.mean_recall_curve[-1] == 1.0, "every relevant chunk is among the candidates"
