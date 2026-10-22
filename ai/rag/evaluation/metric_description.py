"""What the evaluation says about each value it scores: where it is, its name, how it is written, what it does, how it
is computed and what it represents. The evaluation file keeps every description, and the benchmark explorer shows it."""

from dataclasses import dataclass
from enum import Enum


class Display(Enum):
    """How a value is written."""

    SHARE = "share"
    """A share, as a percentage: 72.7%."""
    SCORE = "score"
    """A score from 0 to 1, with three decimals: 0.663."""
    COUNT = "count"
    """A number of things, with one decimal when it is a mean: 61, 41.3."""
    SECONDS = "seconds"
    """A time in seconds: 0.78 s."""


@dataclass(frozen=True)
class MetricDescription:
    path: str
    """Where its value is in the scores of a group, as the evaluation file writes them: `mean_recall_at.10`,
    `question_count`."""
    name: str
    """What a reader calls its value over a group of questions: Recall@10, MRR, Questions."""
    display: Display
    what: str
    """What it does."""
    how: str
    """How it is computed."""
    represents: str
    """What it stands for in the Arbiter."""
