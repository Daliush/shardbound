"""The result of each question, and their averages. Today the retrieval metrics; the answer metrics will join them."""

from dataclasses import dataclass

from rag.evaluation.questions import Question
from rag.evaluation.retrieval_metrics import recall_at_k, reciprocal_rank

RECALL_CUTOFFS = (10, 20)
"""10: the chunks the prompt holds after the reranker. 20: the chunks the reranker receives."""


@dataclass(frozen=True)
class QuestionResult:
    """One question's result: the first chunks ranked for it, kept for the comparator, and its metrics."""

    question_id: str
    category: str
    top_chunk_ids: list[str]
    recall_at: dict[int, float]
    reciprocal_rank: float


@dataclass(frozen=True)
class Scores:
    question_count: int
    mean_recall_at: dict[int, float]
    mean_reciprocal_rank: float


@dataclass(frozen=True)
class Report:
    overall: Scores
    by_category: dict[str, Scores]
    questions: list[QuestionResult]


def score_question(question: Question, ranked_ids: list[str]) -> QuestionResult:
    recall_at = {}
    for k in RECALL_CUTOFFS:
        recall_at[k] = recall_at_k(ranked_ids, question.relevant_ids, k)
    return QuestionResult(question_id=question.id, category=question.category,
                          top_chunk_ids=ranked_ids[:max(RECALL_CUTOFFS)], recall_at=recall_at,
                          reciprocal_rank=reciprocal_rank(ranked_ids, question.relevant_ids))


def average_scores(results: list[QuestionResult]) -> Scores:
    """Every question weighs the same, whatever its number of relevant chunks."""
    recall_sums = {k: 0.0 for k in RECALL_CUTOFFS}
    reciprocal_rank_sum = 0.0
    for result in results:
        for k in RECALL_CUTOFFS:
            recall_sums[k] += result.recall_at[k]
        reciprocal_rank_sum += result.reciprocal_rank
    count = len(results)
    return Scores(question_count=count,
                  mean_recall_at={k: total / count for k, total in recall_sums.items()},
                  mean_reciprocal_rank=reciprocal_rank_sum / count)
