"""Claude through Anthropic's API, with structured outputs: the API holds the reply to the answer's schema."""

import os

import anthropic

from arbiter.generation import Generator
from arbiter.generation.answer import ANSWER_SCHEMA, ANSWER_SCHEMA_HASH
from arbiter.generation.completion import Completion, Usage
from arbiter.generation.completion_cache import CompletionCache
from arbiter.generation.prompt import Prompt

KEY_VARIABLE = "CLAUDE_KEY"
MAX_TOKENS = 16000
"""The ceiling of a reply, thinking included: far above an answer, so that a reply is never cut."""


class ClaudeGenerator(Generator):
    """A Claude model and its effort, how much it thinks before it answers."""

    def __init__(self, name: str, model: str, effort: str, cache: CompletionCache | None = None,
                 client: anthropic.Anthropic | None = None) -> None:
        super().__init__(name, cache)
        self._model = model
        self._effort = effort
        self._client = client

    @property
    def description(self) -> str:
        return f"Claude ({self._model}, effort {self._effort}), with structured outputs"

    @property
    def settings(self) -> dict[str, str | int]:
        return {"model": self._model, "effort": self._effort, "max_tokens": MAX_TOKENS,
                "answer_schema": ANSWER_SCHEMA_HASH}

    def complete_prompt(self, prompt: Prompt) -> Completion:
        response = self._connect().messages.create(
            model=self._model,
            max_tokens=MAX_TOKENS,
            system=prompt.system,
            messages=[{"role": "user", "content": prompt.user}],
            output_config={"effort": self._effort, "format": {"type": "json_schema", "schema": ANSWER_SCHEMA}},
        )
        text = ""
        for block in response.content:
            if block.type == "text":
                text += block.text
        return Completion(text, response.stop_reason, Usage(response.usage.input_tokens, response.usage.output_tokens))

    def _connect(self) -> anthropic.Anthropic:
        """The API's client, built on the first call: building the generator needs no key."""
        if self._client is None:
            key = os.environ.get(KEY_VARIABLE)
            if not key:
                raise SystemExit(f"Set {KEY_VARIABLE} to call Claude.")
            self._client = anthropic.Anthropic(api_key=key)
        return self._client
