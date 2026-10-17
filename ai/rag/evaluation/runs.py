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
    description: str
    """What the layers do, as each describes itself: today the retriever's description."""
    report: Report


@dataclass(frozen=True)
class Evaluation:
    """One `evaluate` command: when, on which code and dataset split, why, and the runs it scored."""

    version: str
    split: str
    evaluated_at: datetime
    code_commit: str
    description: str
    """Why it was run, as whoever ran it wrote it: "Step 6: the cards a question names and the refs expansion"."""
    runs: list[Run]
