"""
The retrieval metrics of one question: how well a ranking holds the chunks it needs. Each is read from the ranks of
the question's relevant chunks in the ranking, which `find_relevant_ranks` finds once, and says what it does, how and
what it represents. Context recall decides; `find_step_metrics` says which metrics score what a step returns:
`RANKING_METRICS` a ranking, `CONTEXT_METRICS` a context.
"""

import math
from abc import ABC, abstractmethod

from rag.contexts import PROMPT_SIZE
from rag.evaluation.metric_description import Display, MetricDescription
from rag.technique import Step

CUTOFFS = (10, 20)
"""The k of Recall@k and binary nDCG@k. The share of the relevant chunks a run ranks at all, however many chunks, is
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
            represents=f"Whether the first {k} chunks hold what the answer needs, in any order: a needed chunk "
                       f"counts as much at rank {k} as at rank 1, and not at all at rank {k + 1}.")
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
            how=f"Each needed chunk in the first {k} earns points, fewer the lower it sits: 1 at rank 1, 0.63 at "
                f"rank 2, 0.5 at rank 3, 0.29 at rank 10. The formula is 1 / log2(rank + 1). A needed chunk past rank "
                f"{k}, like any other chunk, earns nothing. Add up the points, then divide by the best total possible, "
                f"with every needed chunk at the top. Then take the average over all the questions.",
            represents=f"Whether the needed chunks sit high in the first {k}, not just somewhere in them. 1 means "
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


class ContextRecall(RetrievalMetric):
    """
    The decision metric: the share of a question's relevant chunks the prompt would hold. A run that ends with a
    context hands the prompt all of it: `prompt_size` is None. A run that ends with a ranking would hand it its first
    `prompt_size` chunks, so there it is Recall@10.
    """

    def __init__(self, prompt_size: int | None) -> None:
        super().__init__(
            key="context_recall", name="Context recall", display=Display.SHARE,
            what="Out of the chunks a question needs, the share the prompt would hold.",
            how=f"The chunks a question needs are the ones its answer key cites. A run that ends with a context hands "
                f"the prompt every chunk of it; a run that ends with a ranking would hand it its first {PROMPT_SIZE}. "
                f"Count how many of the needed chunks are among them, then divide by how many there are: 6 held out "
                f"of 7 gives 86%. Then take the average over all the questions. For a ranking, it is Recall@"
                f"{PROMPT_SIZE}.",
            represents=f"Whether the prompt holds what the answer needs: an answer cannot rely on a chunk it does not "
                       f"read. It decides between pipelines. Between rankings, every prompt holds {PROMPT_SIZE} "
                       f"chunks; a context holds more, so read it with its number of chunks: a context of every chunk "
                       f"would score 100%.")
        self._prompt_size = prompt_size

    def score(self, relevant_ranks: dict[str, int | None]) -> float:
        """The share of the relevant chunks among the first `prompt_size`, or at any rank when it is None: 6 of 7
        gives 0.86."""
        held = 0
        for rank in relevant_ranks.values():
            if rank is not None and (self._prompt_size is None or rank <= self._prompt_size):
                held += 1
        return held / len(relevant_ranks)


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
"""The metrics that read a ranking's order: Recall and binary nDCG at each cutoff, then the reciprocal rank."""

RANKING_METRICS: list[RetrievalMetric] = [ContextRecall(PROMPT_SIZE), *RETRIEVAL_METRICS]
"""The metrics of a run that ends with a ranking: context recall, its first `PROMPT_SIZE` chunks, then the others. It
holds a metric of every key, in the order a report shows them."""

CONTEXT_METRICS: list[RetrievalMetric] = [ContextRecall(None), RecallAtK(PROMPT_SIZE), BinaryNdcgAtK(PROMPT_SIZE)]
"""The metrics of a run that ends with a context: context recall, every chunk of it, then Recall and binary nDCG at
`PROMPT_SIZE`, which a context keeps from the ranking it cuts when that ranking holds `PROMPT_SIZE` chunks or more.
Past its `PROMPT_SIZE`th chunk, a context's order is no ranking any step reads, so the metrics of a longer cutoff and
the reciprocal rank are left out."""


def find_step_metrics(step: Step) -> list[RetrievalMetric]:
    """The metrics that score what a technique of `step` returns."""
    if step is Step.CONTEXT:
        return CONTEXT_METRICS
    return RANKING_METRICS
