from rag.evaluation.questions import RetrievalQuestion
from rag.evaluation.report import evaluate_retriever, format_report

QUESTIONS = [
    RetrievalQuestion("rule/2.2", "simple-rule", "Two factions in one deck?", frozenset({"2.2"})),
    RetrievalQuestion("anchor", "interaction", "Tempest on my Root Sentinel?", frozenset({"11.3.2", "11.3.3"})),
]
RANKINGS = {
    "Two factions in one deck?": ["2.1", "2.2"],
    "Tempest on my Root Sentinel?": ["11.3.2"] + [f"7.{n}" for n in range(1, 20)] + ["11.3.3"],
}


class FixedRetriever:
    def rank_chunks(self, question: str) -> list[str]:
        return RANKINGS[question]


def test_averages_each_metric_over_the_questions_then_by_category():
    report = evaluate_retriever(FixedRetriever(), QUESTIONS)

    assert report.overall.question_count == 2
    assert report.overall.mean_recall_at == {5: (1.0 + 0.5) / 2, 20: (1.0 + 0.5) / 2}
    assert report.overall.mean_reciprocal_rank == (0.5 + 1.0) / 2
    assert report.by_category["simple-rule"].mean_reciprocal_rank == 0.5
    assert report.by_category["interaction"].mean_recall_at[20] == 0.5


def test_formats_one_row_for_all_the_questions_then_one_per_category():
    table = format_report(evaluate_retriever(FixedRetriever(), QUESTIONS))

    assert table.splitlines()[2:] == [
        "| all | 2 | 75.0% | 75.0% | 0.750 |",
        "| interaction | 1 | 50.0% | 50.0% | 1.000 |",
        "| simple-rule | 1 | 100.0% | 100.0% | 0.500 |",
    ]
