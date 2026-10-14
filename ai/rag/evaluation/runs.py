"""A run: what was evaluated, layer by layer, and its report."""

from dataclasses import dataclass

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
