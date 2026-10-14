"""A report as a Markdown table: one row for all the questions, then one per category."""

from rag.evaluation.scores import RECALL_CUTOFFS, Report, Scores


def format_report_table(report: Report) -> str:
    header = "| Questions | Count | " + " | ".join(f"Recall@{k}" for k in RECALL_CUTOFFS) + " | MRR |"
    separator = "|---|---:|" + "---:|" * (len(RECALL_CUTOFFS) + 1)
    rows = [format_row("all", report.overall)]
    for category, scores in report.by_category.items():
        rows.append(format_row(category, scores))
    return "\n".join([header, separator, *rows])


def format_row(label: str, scores: Scores) -> str:
    recalls = " | ".join(f"{scores.mean_recall_at[k]:.1%}" for k in RECALL_CUTOFFS)
    return f"| {label} | {scores.question_count} | {recalls} | {scores.mean_reciprocal_rank:.3f} |"
