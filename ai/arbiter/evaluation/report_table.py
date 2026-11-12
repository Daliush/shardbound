"""A report of the answers as a Markdown table: one row for all the questions, then one per category."""

from arbiter.evaluation.answer_metrics import ANSWER_METRICS
from arbiter.evaluation.answer_scores import AnswerReport, AnswerScores


def format_report_table(report: AnswerReport) -> str:
    titles = ["Questions", "Count"]
    for metric in ANSWER_METRICS:
        titles.append(metric.description.name)
    titles += ["Tokens in", "Tokens out", "Cost", "Latency p50"]
    header = "| " + " | ".join(titles) + " |"
    separator = "|---|" + "---:|" * (len(titles) - 1)
    rows = [format_row("all", report.overall)]
    for category, scores in report.by_category.items():
        rows.append(format_row(category, scores))
    return "\n".join([header, separator, *rows])


def format_row(label: str, scores: AnswerScores) -> str:
    cells = [label, str(scores.question_count)]
    for metric in ANSWER_METRICS:
        cells.append(format_share(scores.metric_means[metric.key], scores.metric_counts[metric.key]))
    cells += [f"{scores.mean_input_tokens:.0f}", f"{scores.mean_output_tokens:.0f}", f"${scores.mean_cost_dollars:.5f}",
              f"{scores.latency_p50_seconds:.1f} s"]
    return "| " + " | ".join(cells) + " |"


def format_share(mean: float | None, count: int) -> str:
    """41.2% (61), with how many questions the metric scored; a dash when it scored none."""
    if mean is None:
        return "–"
    return f"{mean:.1%} ({count})"
