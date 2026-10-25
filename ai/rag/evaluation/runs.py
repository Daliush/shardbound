"""An evaluation and its runs: what was evaluated, layer by layer, and its report."""

from dataclasses import dataclass
from datetime import datetime

from rag.evaluation.metric_description import MetricDescription
from rag.evaluation.scores import Report


@dataclass(frozen=True)
class Layers:
    """
    What a run evaluates, one name per layer of the Arbiter, in the order a question goes through them: the frontend
    switches between runs by them. Each optional layer is None when the run skipped it: `expand` names the expander
    that adds chunks to the retriever's top, `rerank` the reranker that reorders the candidates, `answer` the
    generator that answers from them (none yet).
    """

    retriever: str
    expand: str | None = None
    rerank: str | None = None
    answer: str | None = None


@dataclass(frozen=True)
class Run:
    layers: Layers
    description: str
    """What the layers do, as each describes itself, the retriever's first."""
    report: Report


@dataclass(frozen=True)
class Evaluation:
    """One `evaluate` command: when, on which code and dataset split, why, and the runs it scored."""

    version: str
    split: str
    evaluated_at: datetime
    code_commit: str
    device: str
    """Where the models ran, which the latency depends on: "cpu", or the GPU's name and torch's build."""
    description: str
    """Why it was run, as whoever ran it wrote it: "Step 6: the cards a question names and the refs expansion"."""
    metrics: list[MetricDescription]
    """Every value of the runs' scores, as the code that computes it describes it: what it does, how, and what it
    represents."""
    runs: list[Run]
