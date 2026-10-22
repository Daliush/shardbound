"""
The retrieval metrics of one question: how well a ranking holds the chunks it needs. Each is read from the ranks of
the question's relevant chunks in the ranking, which `find_relevant_ranks` finds once, and says what it does, how and
what it represents. `RETRIEVAL_METRICS` lists every metric the evaluation scores.
"""

import math
from abc import ABC, abstractmethod

from rag.evaluation.metric_description import Display, MetricDescription

CUTOFFS = {10: "the prompt (the 10 chunks the model will read)",
           20: "the first 20 chunks (what a retriever passes to the next step, and the prompt of the 20-chunk test)"}
"""The k of Recall@k and binary nDCG@k, each with what its first k chunks are. The share of the relevant chunks a run
ranks at all, however many chunks, is the recall curve's last value."""


def find_relevant_ranks(ranked_ids: list[str], relevant_ids: frozenset[str]) -> dict[str, int | None]:
    """Each relevant chunk's rank in the ranking, 1 for the first and None when the ranking leaves it out, by id. A
    chunk ranked twice keeps its first rank."""
    relevant_ranks: dict[str, int | None] = dict.fromkeys(sorted(relevant_ids))
    for rank, chunk_id in enumerate(ranked_ids, start=1):
        if chunk_id in relevant_ranks and relevant_ranks[chunk_id] is None:
            relevant_ranks[chunk_id] = rank
    return relevant_ranks


def describe_cutoff(k: int) -> str:
    """What the first `k` chunks are, as `CUTOFFS` says for 10 and 20; only the first k chunks for a k the evaluation
    does not report."""
    return CUTOFFS.get(k, f"the first {k} chunks")


class RetrievalMetric(ABC):
    """One number for one question, read from the ranks of its relevant chunks. The evaluation averages it over a group
    of questions."""

    def __init__(self, key: str, name: str, display: Display, what: str, how: str, represents: str) -> None:
        self.key = key
        """Where its value is in a question's result: `recall_at.10`. A group's scores hold its mean at `mean_` and
        the key."""
        self.description = MetricDescription(path=f"mean_{key}", name=name, display=display, what=what, how=how,
                                             represents=represents)

    @abstractmethod
    def score(self, relevant_ranks: dict[str, int | None]) -> float:
        """Its value for one question, from each relevant chunk's rank, None when the ranking left it out."""


class RecallAtK(RetrievalMetric):
    def __init__(self, k: int) -> None:
        super().__init__(
            key=f"recall_at.{k}", name=f"Recall@{k}", display=Display.SHARE,
            what=f"Out of the chunks a question needs, the share found in the first {k}.",
            how=f"The chunks a question needs are the ones its answer key cites. Count how many of them are ranked 1 "
                f"to {k}, then divide by how many there are: 3 found out of 4 gives 75%. Then take the average over "
                f"all the questions.",
            represents=f"Whether what the answer needs gets into {describe_cutoff(k)}. A chunk left out can't help the "
                       f"answer.")
        self.k = k

    def score(self, relevant_ranks: dict[str, int | None]) -> float:
        """The share of the relevant chunks ranked among the first `k`: 2 of 4 gives 0.5."""
        found = 0
        for rank in relevant_ranks.values():
            if rank is not None and rank <= self.k:
                found += 1
        return found / len(relevant_ranks)


class BinaryNdcgAtK(RetrievalMetric):
    def __init__(self, k: int) -> None:
        super().__init__(
            key=f"binary_ndcg_at.{k}", name=f"Binary nDCG@{k}", display=Display.SCORE,
            what=f"How close to the top the needed chunks are, in the first {k}. From 0 to 1.",
            how="Each needed chunk earns points, fewer the lower it sits: 1 at rank 1, 0.63 at rank 2, 0.5 at rank 3, "
                "0.29 at rank 10. The formula is 1 / log2(rank + 1). Other chunks earn nothing. Add up the points, "
                "then divide by the best total possible, with every needed chunk at the top. Then take the average "
                "over all the questions.",
            represents=f"Whether the needed chunks sit high in {describe_cutoff(k)}, not just somewhere in it. 1 means "
                       f"they fill the top places. A question whose only needed chunk is at rank {k} gets 100% on "
                       f"Recall@{k}, but only {discount_gain(k):.2f} here.")
        self.k = k

    def score(self, relevant_ranks: dict[str, int | None]) -> float:
        """How high the relevant chunks sit among the first `k`, from 0.0 (none there) to 1.0 (they take the first
        places). Binary: every relevant chunk gains 1 and any other chunk 0, and the rank discounts that gain. The sum
        for the relevant chunks ranked there is divided by the sum for the best ranking, every relevant chunk first: a
        question's only relevant chunk, ranked tenth, gives 0.29. The gains are added in rank order, as the best
        ranking's are, so that a perfect ranking gives exactly 1.0."""
        ranks_in_first_k = []
        for rank in relevant_ranks.values():
            if rank is not None and rank <= self.k:
                ranks_in_first_k.append(rank)
        gains = 0.0
        for rank in sorted(ranks_in_first_k):
            gains += discount_gain(rank)
        best_gains = 0.0
        for rank in range(1, min(len(relevant_ranks), self.k) + 1):
            best_gains += discount_gain(rank)
        return gains / best_gains


def discount_gain(rank: int) -> float:
    """What a gain of 1 is still worth at this rank: 1.0 first, 0.63 second, 0.5 third, 0.29 tenth."""
    return 1 / math.log2(rank + 1)


class ReciprocalRank(RetrievalMetric):
    def __init__(self) -> None:
        super().__init__(
            key="reciprocal_rank", name="MRR", display=Display.SCORE,
            what="How soon the first needed chunk shows up in the list.",
            how="Take 1 divided by the rank of the first needed chunk: 1 at rank 1, 0.5 at rank 2, 0.1 at rank 10, and "
                "0 when the list has none. Then take the average over all the questions.",
            represents="Whether the list starts well. It only looks at the first needed chunk, not at the others.")

    def score(self, relevant_ranks: dict[str, int | None]) -> float:
        """1 / the rank of the first relevant chunk (first: 1.0, third: 0.33), 0.0 when none is ranked."""
        first_rank = None
        for rank in relevant_ranks.values():
            if rank is not None and (first_rank is None or rank < first_rank):
                first_rank = rank
        return 1 / first_rank if first_rank is not None else 0.0


def list_retrieval_metrics() -> list[RetrievalMetric]:
    """Recall at each cutoff, binary nDCG at each cutoff, then the reciprocal rank: the order a report shows them in."""
    metrics: list[RetrievalMetric] = []
    for k in CUTOFFS:
        metrics.append(RecallAtK(k))
    for k in CUTOFFS:
        metrics.append(BinaryNdcgAtK(k))
    metrics.append(ReciprocalRank())
    return metrics


RETRIEVAL_METRICS = list_retrieval_metrics()
