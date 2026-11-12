"""An evaluation of the answers and its runs: what answered, layer by layer, and its report."""

from dataclasses import dataclass
from datetime import datetime

from arbiter.evaluation.answer_scores import AnswerReport
from arbiter.generation.completion import Price
from rag.evaluation.metric_description import MetricDescription
from rag.evaluation.runs import Layers


@dataclass(frozen=True)
class AnswerRun:
    layers: Layers
    """The RAG's techniques, then the generator under `answer`."""
    description: str
    """What gave the context, then what wrote the answer, as the generator describes itself."""
    generator_settings: dict[str, str | int]
    """Everything that changes what the model returns, as the cache keys a completion by it."""
    price: Price
    """The price the costs were computed at."""
    report: AnswerReport


@dataclass(frozen=True)
class AnswerEvaluation:
    """One `evaluate` command: when, on which code and dataset split, why, and the runs it scored."""

    version: str
    split: str
    evaluated_at: datetime
    code_commit: str
    device: str
    """Where the RAG's models ran, which chose the context: "cpu" or the GPU's name, then torch's build. The
    answers' time is the API call's, not the device's."""
    description: str
    """Why it was run, as whoever ran it wrote it: "Step 9: the first checks of the answers"."""
    metrics: list[MetricDescription]
    """Every value of the runs' scores, as the code that computes it describes it: what it does, how, and what it
    represents."""
    runs: list[AnswerRun]
