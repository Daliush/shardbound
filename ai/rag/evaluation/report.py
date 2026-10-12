"""Scores a retriever on a list of questions: the mean of each metric, over all of them and by category."""

from dataclasses import dataclass

from rag.evaluation.metrics import recall_at_k, reciprocal_rank
from rag.evaluation.questions import RetrievalQuestion
from rag.retrievers import Retriever

RECALL_CUTOFFS = (5, 20)
"""5: the chunks the prompt holds after the reranker. 20: the chunks the reranker receives."""


@dataclass(frozen=True)
class RetrievalScores:
    question_count: int
    mean_recall_at: dict[int, float]
    mean_reciprocal_rank: float


@dataclass(frozen=True)
class RetrievalReport:
    overall: RetrievalScores
    by_category: dict[str, RetrievalScores]


def evaluate_retriever(retriever: Retriever, questions: list[RetrievalQuestion]) -> RetrievalReport:
    rankings = {question.id: retriever.rank_chunks(question.text) for question in questions}
    categories = sorted({question.category for question in questions})
    return RetrievalReport(
        overall=score_questions(questions, rankings),
        by_category={category: score_questions([question for question in questions if question.category == category],
                                               rankings)
                     for category in categories})


def score_questions(questions: list[RetrievalQuestion], rankings: dict[str, list[str]]) -> RetrievalScores:
    """Every question weighs the same, whatever its number of relevant chunks."""
    def mean(values: list[float]) -> float:
        return sum(values) / len(values)

    return RetrievalScores(
        question_count=len(questions),
        mean_recall_at={k: mean([recall_at_k(rankings[question.id], question.relevant_ids, k) for question in questions])
                        for k in RECALL_CUTOFFS},
        mean_reciprocal_rank=mean([reciprocal_rank(rankings[question.id], question.relevant_ids)
                                   for question in questions]))


def format_report(report: RetrievalReport) -> str:
    """A Markdown table: one row for all the questions, then one per category."""
    header = "| Questions | Count | " + " | ".join(f"Recall@{k}" for k in RECALL_CUTOFFS) + " | MRR |"
    separator = "|---|---:|" + "---:|" * (len(RECALL_CUTOFFS) + 1)
    rows = [format_row("all", report.overall)]
    rows += [format_row(category, scores) for category, scores in report.by_category.items()]
    return "\n".join([header, separator, *rows])


def format_row(label: str, scores: RetrievalScores) -> str:
    recalls = " | ".join(f"{scores.mean_recall_at[k]:.1%}" for k in RECALL_CUTOFFS)
    return f"| {label} | {scores.question_count} | {recalls} | {scores.mean_reciprocal_rank:.3f} |"
