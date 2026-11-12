"""The generators: each writes the Arbiter's answer from a prompt, with one model and its settings."""

from abc import ABC, abstractmethod
from dataclasses import dataclass

from pydantic import ValidationError

from arbiter.generation.answer import Answer
from arbiter.generation.completion import Completion, Price, Usage
from arbiter.generation.completion_cache import CompletionCache
from arbiter.generation.prompt import Prompt

MAX_ATTEMPTS = 2
"""A first try, and one more when the reply is not a valid answer (design doc §5.2)."""


@dataclass(frozen=True)
class Generation:
    """The answer, or None when no attempt gave a valid one; why each failed attempt failed, for the format failures
    the evaluation counts; and the tokens and the time of every attempt."""

    answer: Answer | None
    format_failures: list[str]
    usage: Usage
    seconds: float


class Generator(ABC):
    """One way of writing the answer, under the name its registry gives it."""

    def __init__(self, name: str, cache: CompletionCache | None = None) -> None:
        self.name = name
        self._cache = cache

    @property
    @abstractmethod
    def description(self) -> str:
        """What it is, written from its settings for whoever reads an evaluation."""

    @property
    @abstractmethod
    def settings(self) -> dict[str, str | int]:
        """Everything that changes what the model returns, its model's id first: a completion is cached by them."""

    @property
    @abstractmethod
    def price(self) -> Price:
        """What its model's tokens cost, for the cost of each answer."""

    @abstractmethod
    def complete_prompt(self, prompt: Prompt) -> Completion:
        """The model's reply to `prompt`, as it came, and how long the call took."""

    def generate_answer(self, prompt: Prompt) -> Generation:
        """The reply of the first attempt that is a valid answer, at most `MAX_ATTEMPTS`."""
        format_failures = []
        usage = Usage(0, 0)
        seconds = 0.0
        for attempt in range(1, MAX_ATTEMPTS + 1):
            completion = self._complete_attempt(prompt, attempt)
            usage = usage.add(completion.usage)
            seconds += completion.seconds
            try:
                return Generation(Answer.model_validate_json(completion.text), format_failures, usage, seconds)
            except ValidationError as error:
                format_failures.append(describe_format_failure(completion, error))
        return Generation(None, format_failures, usage, seconds)

    def _complete_attempt(self, prompt: Prompt, attempt: int) -> Completion:
        """The cached completion of this attempt when there is one; else the model's, kept."""
        if self._cache is None:
            return self.complete_prompt(prompt)
        completion = self._cache.find_completion(self.settings, prompt, attempt)
        if completion is None:
            completion = self.complete_prompt(prompt)
            self._cache.save_completion(self.settings, prompt, attempt, completion)
        return completion


def describe_format_failure(completion: Completion, error: ValidationError) -> str:
    """Why a reply is no answer, in one line: why the model stopped, then the first error and where it is."""
    first_error = error.errors(include_url=False)[0]
    failure = f"stopped on {completion.stop_reason}: {first_error['msg']}"
    if first_error["loc"]:
        failure += f" ({'.'.join(map(str, first_error['loc']))})"
    return failure
