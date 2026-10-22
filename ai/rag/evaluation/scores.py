"""The result of each question, and the scores of a group of them. Today the retrieval metrics; the answer metrics will
join them."""

import math
from dataclasses import dataclass

from rag.evaluation.metric_description import Display, MetricDescription
from rag.evaluation.questions import Question
from rag.evaluation.retrieval_metrics import CUTOFFS, RETRIEVAL_METRICS, RecallAtK, find_relevant_ranks

LATENCY_MEANING = "How long a user waits for the chunks, on the computer that ran the evaluation. Lower is better."

GROUP_VALUE_DESCRIPTIONS = [
    MetricDescription(
        path="question_count", name="Questions", display=Display.COUNT,
        what="How many questions were scored.",
        how="Every question of the split, or of one category, that has an answer. A question with no answer needs no "
            "chunk, so it is left out.",
        represents="How much to trust a score. The fewer the questions, the more a single question moves it."),
    MetricDescription(
        path="mean_candidate_count", name="Candidates", display=Display.COUNT,
        what="How many chunks a pipeline returns for a question, on average.",
        how="Count the chunks in the list the pipeline's last step returns, then take the average over all the "
            "questions.",
        represents="Context, not a score to win: a list of every chunk would hold every needed chunk. Read it with the "
                   "end of the recall curve."),
    MetricDescription(
        path="mean_recall_curve", name="Recall curve", display=Display.SHARE,
        what="Recall for every list length, from 1 chunk to the whole list.",
        how="For each length k, the average Recall@k over all the questions. When a question's list is shorter than k, "
            "it keeps the recall of its full list.",
        represents="It compares pipelines at the same number of chunks. Its last point is the share of the needed "
                   "chunks found anywhere in the list. After the expansion, that is the most a reranker could keep, "
                   "since a reranker only reorders what it gets; after a retriever alone, a reranker gets only its "
                   "first 20."),
    MetricDescription(
        path="latency_p50_seconds", name="Latency p50", display=Display.SECONDS,
        what="How long a typical question takes.",
        how="For each question, add up the time of every step. The p50 is the time that half of the questions don't "
            "go over.",
        represents=LATENCY_MEANING),
    MetricDescription(
        path="latency_p95_seconds", name="Latency p95", display=Display.SECONDS,
        what="How long the slowest questions take.",
        how="For each question, add up the time of every step. The p95 is the time that 95% of the questions don't "
            "go over.",
        represents=LATENCY_MEANING),
]
"""The values of a group that are not the mean of a metric, which `score_group` computes itself."""


@dataclass(frozen=True)
class QuestionResult:
    """One question's result: the first chunks ranked for it, kept for the comparator, and its metrics."""

    question_id: str
    category: str
    top_chunk_ids: list[str]
    relevant_ranks: dict[str, int | None]
    """Each relevant chunk's rank in the whole list the retriever returned, None when it left it out: every metric is
    read from them, and the comparator sees where a chunk landed past the first 20."""
    metric_values: dict[str, float]
    """Each retrieval metric's value for this question, by the metric's key: `recall_at.10`."""
    candidate_count: int
    """How many chunks the run ranked: the retriever's whole list, or the candidates the reranker reordered."""
    seconds: float
    """How long every step of the run took for this question."""


@dataclass(frozen=True)
class Scores:
    """The scores of a group of questions: all the questions of a run, or those of one category."""

    question_count: int
    metric_means: dict[str, float]
    """Each retrieval metric averaged over the questions, by the metric's key."""
    mean_candidate_count: float
    mean_recall_curve: list[float]
    """Recall@k averaged over the questions, for every k from 1 to the longest list a question got, the first value
    being Recall@1. Past its end it stays at its last value, the share of the relevant chunks among every chunk ranked:
    after the expansion, the most a reranker could keep. It compares retrievers at the same number of chunks."""
    latency_p50_seconds: float
    latency_p95_seconds: float


@dataclass(frozen=True)
class Report:
    overall: Scores
    by_category: dict[str, Scores]
    questions: list[QuestionResult]


def list_metric_descriptions() -> list[MetricDescription]:
    """Every value the scores of a group hold, described: each retrieval metric, then the values only a group has."""
    descriptions = []
    for metric in RETRIEVAL_METRICS:
        descriptions.append(metric.description)
    descriptions.extend(GROUP_VALUE_DESCRIPTIONS)
    return descriptions


def score_question(question: Question, ranked_ids: list[str], seconds: float) -> QuestionResult:
    """One question's result: the rank of each of its relevant chunks in `ranked_ids`, then the value of each retrieval
    metric, read from these ranks."""
    relevant_ranks = find_relevant_ranks(ranked_ids, question.relevant_ids)
    metric_values = {}
    for metric in RETRIEVAL_METRICS:
        metric_values[metric.key] = metric.score(relevant_ranks)
    return QuestionResult(question_id=question.id, category=question.category,
                          top_chunk_ids=ranked_ids[:max(CUTOFFS)], relevant_ranks=relevant_ranks,
                          metric_values=metric_values, candidate_count=len(ranked_ids), seconds=seconds)


def score_group(results: list[QuestionResult]) -> Scores:
    """
    The scores of a group of questions: all the questions of a run, or those of one category. Each retrieval metric is
    averaged over the questions, every question weighing the same whatever its number of relevant chunks. The values
    that are not the mean of a metric are computed here too: the number of questions, the mean number of candidates,
    the recall curve, and the latency p50 and p95, read from the time each question took.
    """
    seconds = list_seconds(results)
    return Scores(question_count=len(results),
                  metric_means=average_metric_values(results),
                  mean_candidate_count=average_candidate_count(results),
                  mean_recall_curve=average_recall_curve(results),
                  latency_p50_seconds=find_percentile(seconds, 50),
                  latency_p95_seconds=find_percentile(seconds, 95))


def average_metric_values(results: list[QuestionResult]) -> dict[str, float]:
    """Each retrieval metric's value averaged over the questions, by the metric's key."""
    means = {}
    for metric in RETRIEVAL_METRICS:
        key = metric.key
        total = 0.0
        for result in results:
            total += result.metric_values[key]
        means[key] = total / len(results)
    return means


def average_candidate_count(results: list[QuestionResult]) -> float:
    total = 0
    for result in results:
        total += result.candidate_count
    return total / len(results)


def list_seconds(results: list[QuestionResult]) -> list[float]:
    seconds = []
    for result in results:
        seconds.append(result.seconds)
    return seconds


def find_percentile(values: list[float], percent: int) -> float:
    """The smallest value that `percent`% of the values do not exceed: of 61 questions, the p95 is the 58th fastest."""
    return sorted(values)[math.ceil(len(values) * percent / 100) - 1]


def average_recall_curve(results: list[QuestionResult]) -> list[float]:
    """Recall@k averaged over the questions, for every k from 1 to the longest list: past the end of its own list, a
    question keeps the recall its whole list reached."""
    longest = 0
    for result in results:
        longest = max(longest, result.candidate_count)
    curve = []
    for k in range(1, longest + 1):
        recall_at_k = RecallAtK(k)
        recall_sum = 0.0
        for result in results:
            recall_sum += recall_at_k.score(result.relevant_ranks)
        curve.append(recall_sum / len(results))
    return curve
