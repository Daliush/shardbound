from rag.evaluation.report_table import format_report_table
from rag.evaluation.scores import Report, Scores


def build_scores(question_count: int, recall: float, ndcg: float, reciprocal_rank: float,
                 candidate_count: float) -> Scores:
    return Scores(question_count=question_count,
                  metric_means={"recall_at.10": recall, "recall_at.20": recall, "binary_ndcg_at.10": ndcg,
                                "binary_ndcg_at.20": ndcg, "reciprocal_rank": reciprocal_rank},
                  mean_candidate_count=candidate_count,
                  mean_recall_curve=[recall], latency_p50_seconds=0.0, latency_p95_seconds=0.0)


def test_formats_one_row_for_all_the_questions_then_one_per_category():
    report = Report(overall=build_scores(2, 0.75, 0.6, 0.75, 30.5),
                    by_category={"interaction": build_scores(1, 0.5, 0.7, 1.0, 41.0),
                                 "simple-rule": build_scores(1, 1.0, 0.5, 0.5, 20.0)},
                    questions=[])

    assert format_report_table(report).splitlines() == [
        "| Questions | Count | Recall@10 | Recall@20 | Binary nDCG@10 | Binary nDCG@20 | MRR | Candidates |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
        "| all | 2 | 75.0% | 75.0% | 0.600 | 0.600 | 0.750 | 30.5 |",
        "| interaction | 1 | 50.0% | 50.0% | 0.700 | 0.700 | 1.000 | 41.0 |",
        "| simple-rule | 1 | 100.0% | 100.0% | 0.500 | 0.500 | 0.500 | 20.0 |",
    ]
