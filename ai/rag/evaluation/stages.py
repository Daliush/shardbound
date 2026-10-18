"""The nodes of the evaluation's tree: each is one technique applied to every question, after the stage before it."""

from dataclasses import dataclass

from rag.evaluation.questions import Question
from rag.evaluation.runs import Layers
from rag.retrievers import CANDIDATE_COUNT
from rag.technique import Step, Technique


@dataclass(frozen=True)
class StepResult:
    """What a technique handed on for one question, and how long it took."""

    question: Question
    chunk_ids: list[str]
    seconds: float


@dataclass(frozen=True)
class Stage:
    """
    One technique applied to every question, to what its parent handed on: a retriever has no parent. Going up the
    parents gives the pipeline that produced it, what each of its steps handed on, and the run's name.
    """

    technique: Technique
    parent: "Stage | None"
    results: dict[str, StepResult]
    """By question id."""

    def hand_on(self, question: Question) -> list[str]:
        """What the next step reads for the question: a retriever's first `CANDIDATE_COUNT` chunks, the whole list of
        any other step."""
        chunk_ids = self.results[question.id].chunk_ids
        if self.technique.step is Step.RETRIEVER:
            return chunk_ids[:CANDIDATE_COUNT]
        return chunk_ids

    def name_layers(self) -> Layers:
        """The technique of each step of the pipeline, None for a step it skipped."""
        names = {}
        stage = self
        while stage is not None:
            names[stage.technique.step.value] = stage.technique.name
            stage = stage.parent
        return Layers(**names)

    def describe(self) -> str:
        """What each step of the pipeline does, the retriever's first."""
        if self.parent is None:
            return self.technique.description
        handed_on = f"its top {CANDIDATE_COUNT} " if self.parent.technique.step is Step.RETRIEVER else ""
        return f"{self.parent.describe()}; {handed_on}{self.technique.description}"

    def sum_seconds(self, question: Question) -> float:
        """How long every step of the pipeline took for the question."""
        seconds = self.results[question.id].seconds
        if self.parent is None:
            return seconds
        return seconds + self.parent.sum_seconds(question)
