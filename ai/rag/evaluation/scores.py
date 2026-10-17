"""The result of each question, and their averages. Today the retrieval metrics; the answer metrics will join them."""

from dataclasses import dataclass

from rag.evaluation.questions import Question
from rag.evaluation.retrieval_metrics import find_relevant_ranks, recall_at_k, reciprocal_rank

RECALL_CUTOFFS = (10, 20)
"""10: the chunks the prompt holds after the reranker. 20: the top 20 the refs expansion starts from, and the prompt of
the 20-chunk variant. The share of the relevant chunks the reranker receives, however many candidates, is the recall
curve's last value."""


@dataclass(frozen=True)
class QuestionResult:
    """One question's result: the first chunks ranked for it, kept for the comparator, and its metrics."""

    question_id: str
    category: str
    top_chunk_ids: list[str]
    relevant_ranks: dict[str, int | None]
    """Each relevant chunk's rank in the whole list the retriever returned, None when it left it out: `recall_at`
    and `reciprocal_rank` are read from them, and the comparator sees where a chunk landed past the first 20."""
    recall_at: dict[int, float]
    reciprocal_rank: float
    candidate_count: int
    """How many chunks the retriever returned: the candidates a reranker receives."""


@dataclass(frozen=True)
class Scores:
    question_count: int
    mean_recall_at: dict[int, float]
    mean_reciprocal_rank: float
    mean_candidate_count: float
    mean_recall_curve: list[float]
    """Recall@k averaged over the questions, for every k from 1 to the longest list a question got, the first value
    being Recall@1. Past its end it stays at its last value, the share of the relevant chunks among every candidate:
    the most a reranker could keep. It compares retrievers at the same number of chunks."""


@dataclass(frozen=True)
class Report:
    overall: Scores
    by_category: dict[str, Scores]
    questions: list[QuestionResult]


def score_question(question: Question, ranked_ids: list[str]) -> QuestionResult:
    relevant_ranks = find_relevant_ranks(ranked_ids, question.relevant_ids)
    recall_at = {}
    for k in RECALL_CUTOFFS:
        recall_at[k] = recall_at_k(relevant_ranks, k)
    return QuestionResult(question_id=question.id, category=question.category,
                          top_chunk_ids=ranked_ids[:max(RECALL_CUTOFFS)], relevant_ranks=relevant_ranks,
                          recall_at=recall_at, reciprocal_rank=reciprocal_rank(relevant_ranks),
                          candidate_count=len(ranked_ids))


def average_scores(results: list[QuestionResult]) -> Scores:
    """Every question weighs the same, whatever its number of relevant chunks."""
    recall_sums = {k: 0.0 for k in RECALL_CUTOFFS}
    reciprocal_rank_sum = 0.0
    candidate_count_sum = 0
    for result in results:
        for k in RECALL_CUTOFFS:
            recall_sums[k] += result.recall_at[k]
        reciprocal_rank_sum += result.reciprocal_rank
        candidate_count_sum += result.candidate_count
    count = len(results)
    return Scores(question_count=count,
                  mean_recall_at={k: total / count for k, total in recall_sums.items()},
                  mean_reciprocal_rank=reciprocal_rank_sum / count,
                  mean_candidate_count=candidate_count_sum / count,
                  mean_recall_curve=average_recall_curve(results))


def average_recall_curve(results: list[QuestionResult]) -> list[float]:
    """Recall@k averaged over the questions, for every k from 1 to the longest list: past the end of its own list, a
    question keeps the recall its whole list reached."""
    longest = 0
    for result in results:
        longest = max(longest, result.candidate_count)
    curve = []
    for k in range(1, longest + 1):
        recall_sum = 0.0
        for result in results:
            recall_sum += recall_at_k(result.relevant_ranks, k)
        curve.append(recall_sum / len(results))
    return curve
