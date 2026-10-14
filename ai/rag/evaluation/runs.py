"""An evaluation and its runs: what was evaluated, layer by layer, and its report."""

from dataclasses import dataclass
from datetime import datetime

from rag.evaluation.scores import Report


@dataclass(frozen=True)
class Layers:
    """
    What a run evaluates, one name per layer of the Arbiter: the frontend switches between runs by them. `answer`
    names the generator that answers from the retrieved chunks: None until there is one.
    """

    retriever: str
    answer: str | None = None


@dataclass(frozen=True)
class Run:
    layers: Layers
    report: Report


@dataclass(frozen=True)
class Evaluation:
    """One `evaluate` command: when, on which code and dataset split, and the runs it scored."""

    version: str
    split: str
    evaluated_at: datetime
    code_commit: str
    runs: list[Run]
