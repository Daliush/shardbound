"""What a model returns for a prompt, before anything checks it, and what it costs."""

from dataclasses import dataclass


@dataclass(frozen=True)
class Usage:
    """The tokens a call cost: the model's thinking counts in the output."""

    input_tokens: int
    output_tokens: int

    def add(self, other: "Usage") -> "Usage":
        return Usage(self.input_tokens + other.input_tokens, self.output_tokens + other.output_tokens)


@dataclass(frozen=True)
class Price:
    """What a model's tokens cost, in US dollars per million."""

    input_per_million: float
    output_per_million: float

    def cost_of(self, usage: Usage) -> float:
        """The dollars `usage` costs: 1,600 tokens in and 600 out, at $0.10 and $0.50 a million, cost $0.00046."""
        return (usage.input_tokens * self.input_per_million
                + usage.output_tokens * self.output_per_million) / 1_000_000


@dataclass(frozen=True)
class Completion:
    """The model's text, why it stopped ("end_turn", "max_tokens", "refusal"…), what it cost and how long it took."""

    text: str
    stop_reason: str
    usage: Usage
    seconds: float
    """How long the call took. The cache keeps it, so a cached completion still says how long the model took."""
