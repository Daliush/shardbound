"""What a model returns for a prompt, before anything checks it."""

from dataclasses import dataclass


@dataclass(frozen=True)
class Usage:
    """The tokens a call cost: the model's thinking counts in the output."""

    input_tokens: int
    output_tokens: int

    def add(self, other: "Usage") -> "Usage":
        return Usage(self.input_tokens + other.input_tokens, self.output_tokens + other.output_tokens)


@dataclass(frozen=True)
class Completion:
    """The model's text, why it stopped ("end_turn", "max_tokens", "refusal"…) and what it cost."""

    text: str
    stop_reason: str
    usage: Usage
