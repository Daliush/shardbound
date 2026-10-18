from rag.evaluation.report_table import format_report_table
from rag.evaluation.scores import Report, Scores


def build_scores(question_count: int, recall: float, reciprocal_rank: float, candidate_count: float) -> Scores:
    return Scores(question_count=question_count, mean_recall_at={10: recall, 20: recall},
                  mean_reciprocal_rank=reciprocal_rank, mean_candidate_count=candidate_count,
                  mean_recall_curve=[recall], latency_p50_seconds=0.0, latency_p95_seconds=0.0)


def test_formats_one_row_for_all_the_questions_then_one_per_category():
    report = Report(overall=build_scores(2, 0.75, 0.75, 30.5),
                    by_category={"interaction": build_scores(1, 0.5, 1.0, 41.0),
                                 "simple-rule": build_scores(1, 1.0, 0.5, 20.0)},
                    questions=[])

    assert format_report_table(report).splitlines() == [
        "| Questions | Count | Recall@10 | Recall@20 | MRR | Candidates |",
        "|---|---:|---:|---:|---:|---:|",
        "| all | 2 | 75.0% | 75.0% | 0.750 | 30.5 |",
        "| interaction | 1 | 50.0% | 50.0% | 1.000 | 41.0 |",
        "| simple-rule | 1 | 100.0% | 100.0% | 0.500 | 20.0 |",
    ]
