"""What the evaluation says about each metric it scores: where its value is, its name, and how its value is written."""

from dataclasses import dataclass
from enum import Enum


class Display(Enum):
    """How a metric's value is written."""

    SHARE = "share"
    """A share of chunks, as a percentage: 72.7%."""
    SCORE = "score"
    """A score from 0 to 1, with three decimals: 0.663."""


@dataclass(frozen=True)
class MetricDescription:
    key: str
    """Where its value is: `recall_at.10` in a question's result, `mean_recall_at.10` in the scores of a group."""
    name: str
    """What a report calls its mean over a group of questions: Recall@10, MRR."""
    display: Display
