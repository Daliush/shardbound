from rag.evaluation.report_table import format_report_table
from rag.evaluation.scores import Report, Scores


def test_formats_one_row_for_all_the_questions_then_one_per_category():
    report = Report(
        overall=Scores(2, {10: 0.75, 20: 0.75}, 0.75),
        by_category={"interaction": Scores(1, {10: 0.5, 20: 0.5}, 1.0),
                     "simple-rule": Scores(1, {10: 1.0, 20: 1.0}, 0.5)},
        questions=[])

    assert format_report_table(report).splitlines() == [
        "| Questions | Count | Recall@10 | Recall@20 | MRR |",
        "|---|---:|---:|---:|---:|",
        "| all | 2 | 75.0% | 75.0% | 0.750 |",
        "| interaction | 1 | 50.0% | 50.0% | 1.000 |",
        "| simple-rule | 1 | 100.0% | 100.0% | 0.500 |",
    ]
