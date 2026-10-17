"""A report as a Markdown table: one row for all the questions, then one per category."""

from rag.evaluation.scores import RECALL_CUTOFFS, Report, Scores


def format_report_table(report: Report) -> str:
    titles = ["Questions", "Count"]
    for k in RECALL_CUTOFFS:
        titles.append(f"Recall@{k}")
    titles.extend(["MRR", "Candidate recall", "Candidates"])
    header = "| " + " | ".join(titles) + " |"
    separator = "|---|" + "---:|" * (len(titles) - 1)
    rows = [format_row("all", report.overall)]
    for category, scores in report.by_category.items():
        rows.append(format_row(category, scores))
    return "\n".join([header, separator, *rows])


def format_row(label: str, scores: Scores) -> str:
    recalls = " | ".join(f"{scores.mean_recall_at[k]:.1%}" for k in RECALL_CUTOFFS)
    return (f"| {label} | {scores.question_count} | {recalls} | {scores.mean_reciprocal_rank:.3f} "
            f"| {scores.mean_candidate_recall:.1%} | {scores.mean_candidate_count:.1f} |")
