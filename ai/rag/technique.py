"""The steps of the Arbiter's pipeline, and what every way of doing one of them has in common."""

from abc import ABC, abstractmethod
from enum import Enum


class Step(Enum):
    """The steps a question goes through, in this order. Each value names a layer of a run."""

    RETRIEVER = "retriever"
    EXPAND = "expand"
    RERANK = "rerank"


class Technique(ABC):
    """One way of doing a step: a retriever, an expander or a reranker, under the name its registry gives it."""

    step: Step

    def __init__(self, name: str) -> None:
        self.name = name

    @property
    @abstractmethod
    def description(self) -> str:
        """What it does, written from its settings for whoever reads an evaluation: a phrase another technique's
        description can hold, such as "RRF (k = 60) of BM25 (…) and dense retrieval (…)"."""

    @abstractmethod
    def process(self, question: str, chunk_ids: list[str]) -> list[str]:
        """The chunk ids it hands on for `question`, from those the step before handed it: none for a retriever."""
