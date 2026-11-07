from arbiter.evaluation.answer_scores import AnswerScores, report_answers
from arbiter.evaluation.report_table import format_report_table, format_row
from tests.arbiter.evaluation.test_evaluation_json import build_result


def test_prints_one_row_for_all_the_questions_then_one_per_category():
    table = format_report_table(report_answers([build_result(True), build_result(False)]))

    lines = table.splitlines()
    assert lines[0] == ("| Questions | Count | Valid answers | Answered | Correct refusals | Citation recall | "
                        "Citation precision | Step order | Tokens in | Tokens out | Cost | Latency p50 |")
    assert lines[2].startswith("| all | 2 | 50.0% (2) | 50.0% (2) | – | 50.0% (2) | 100.0% (1) | – |")
    assert lines[3].startswith("| simple-rule | 2 |")


def test_prints_the_tokens_the_cost_and_the_latency_of_a_question():
    scores = AnswerScores(question_count=1, metric_means={"valid_answer": 1.0, "answered": None,
                                                          "correct_refusal": 1.0, "citation_recall": None,
                                                          "citation_precision": None, "step_order": None},
                          metric_counts={"valid_answer": 1, "answered": 0, "correct_refusal": 1,
                                         "citation_recall": 0, "citation_precision": 0, "step_order": 0},
                          mean_input_tokens=1632, mean_output_tokens=615, mean_cost_dollars=0.00047,
                          latency_p50_seconds=4.24, latency_p95_seconds=9.0)

    assert format_row("factual", scores).endswith("| 1632 | 615 | $0.00047 | 4.2 s |")
