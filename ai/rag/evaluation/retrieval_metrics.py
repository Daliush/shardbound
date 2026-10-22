"""
The retrieval metrics of one question: how well a ranking holds the chunks it needs. Each is read from the ranks of
the question's relevant chunks in the ranking, which `find_relevant_ranks` finds once. `RETRIEVAL_METRICS` lists every
metric the evaluation scores.
"""

from abc import ABC, abstractmethod

from rag.evaluation.metric_description import Display, MetricDescription

CUTOFFS = (10, 20)
"""The k of Recall@k. 10: the chunks the prompt holds after the reranker. 20: the top 20 the refs expansion starts from,
and the prompt of the 20-chunk variant. The share of the relevant chunks a run ranks at all, however many chunks, is
the recall curve's last value."""


def find_relevant_ranks(ranked_ids: list[str], relevant_ids: frozenset[str]) -> dict[str, int | None]:
    """Each relevant chunk's rank in the ranking, 1 for the first and None when the ranking leaves it out, by id. A
    chunk ranked twice keeps its first rank."""
    relevant_ranks: dict[str, int | None] = dict.fromkeys(sorted(relevant_ids))
    for rank, chunk_id in enumerate(ranked_ids, start=1):
        if chunk_id in relevant_ranks and relevant_ranks[chunk_id] is None:
            relevant_ranks[chunk_id] = rank
    return relevant_ranks


class RetrievalMetric(ABC):
    """One number for one question, read from the ranks of its relevant chunks. The evaluation averages it over a group
    of questions."""

    def __init__(self, description: MetricDescription) -> None:
        self.description = description

    @abstractmethod
    def score(self, relevant_ranks: dict[str, int | None]) -> float:
        """Its value for one question, from each relevant chunk's rank, None when the ranking left it out."""


class RecallAtK(RetrievalMetric):
    def __init__(self, k: int) -> None:
        super().__init__(MetricDescription(key=f"recall_at.{k}", name=f"Recall@{k}", display=Display.SHARE))
        self.k = k

    def score(self, relevant_ranks: dict[str, int | None]) -> float:
        """The share of the relevant chunks ranked among the first `k`: 2 of 4 gives 0.5."""
        found = 0
        for rank in relevant_ranks.values():
            if rank is not None and rank <= self.k:
                found += 1
        return found / len(relevant_ranks)


class ReciprocalRank(RetrievalMetric):
    def __init__(self) -> None:
        super().__init__(MetricDescription(key="reciprocal_rank", name="MRR", display=Display.SCORE))

    def score(self, relevant_ranks: dict[str, int | None]) -> float:
        """1 / the rank of the first relevant chunk (first: 1.0, third: 0.33), 0.0 when none is ranked."""
        first_rank = None
        for rank in relevant_ranks.values():
            if rank is not None and (first_rank is None or rank < first_rank):
                first_rank = rank
        return 1 / first_rank if first_rank is not None else 0.0


def list_retrieval_metrics() -> list[RetrievalMetric]:
    """Recall at each cutoff, then the reciprocal rank: the order a report shows them in."""
    metrics: list[RetrievalMetric] = []
    for k in CUTOFFS:
        metrics.append(RecallAtK(k))
    metrics.append(ReciprocalRank())
    return metrics


RETRIEVAL_METRICS = list_retrieval_metrics()
