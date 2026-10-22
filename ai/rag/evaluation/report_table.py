"""A report as a Markdown table: one row for all the questions, then one per category."""

from rag.evaluation.metric_description import Display
from rag.evaluation.retrieval_metrics import RETRIEVAL_METRICS
from rag.evaluation.scores import Report, Scores


def format_report_table(report: Report) -> str:
    titles = ["Questions", "Count"]
    for metric in RETRIEVAL_METRICS:
        titles.append(metric.description.name)
    titles.append("Candidates")
    header = "| " + " | ".join(titles) + " |"
    separator = "|---|" + "---:|" * (len(titles) - 1)
    rows = [format_row("all", report.overall)]
    for category, scores in report.by_category.items():
        rows.append(format_row(category, scores))
    return "\n".join([header, separator, *rows])


def format_row(label: str, scores: Scores) -> str:
    cells = [label, str(scores.question_count)]
    for metric in RETRIEVAL_METRICS:
        cells.append(format_value(metric.description.display, scores.metric_means[metric.description.key]))
    cells.append(f"{scores.mean_candidate_count:.1f}")
    return "| " + " | ".join(cells) + " |"


def format_value(display: Display, value: float) -> str:
    """72.7% for a share, 0.663 for a score."""
    if display is Display.SHARE:
        return f"{value:.1%}"
    return f"{value:.3f}"
