"""A comparison as a Markdown table: one row per run found in both evaluations, then the runs new to the second."""

from rag.comparison.comparison_service import Comparison, RunComparison


def format_comparison_table(comparison: Comparison) -> str:
    lines = ["| Run | Decided by | Before -> after | Chunks | Questions ranked differently |",
             "|---|---|---:|---:|---|"]
    for run in comparison.runs:
        lines.append(format_row(run))
    if comparison.new_run_names:
        lines.append("\nNot in the recorded evaluation, so not compared: " + ", ".join(comparison.new_run_names))
    return "\n".join(lines)


def format_row(run: RunComparison) -> str:
    reranked = ", ".join(run.reranked_question_ids) or "none"
    return (f"| {run.name} | {run.metric_name} | {run.before:.1%} -> {run.after:.1%} "
            f"| {run.candidates_before:.1f} -> {run.candidates_after:.1f} | {reranked} |")
