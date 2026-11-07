from arbiter.generation import Generator
from arbiter.generation.completion import Completion, Price, Usage
from arbiter.generation.completion_cache import CompletionCache
from arbiter.generation.prompt import Prompt

VALID_REPLY = '{"answer": "Ash Warden is destroyed.", "citations": ["8.2"], "answerable": true}'
SCRIPTED_PRICE = Price(input_per_million=1.0, output_per_million=5.0)
SECONDS_PER_CALL = 1.5


class ScriptedGenerator(Generator):
    """Replies with the texts it is given, one per call, each 100 tokens in and 20 out in 1.5 s, and keeps the prompts
    it read: the model, in tests."""

    def __init__(self, replies: list[str], cache: CompletionCache | None = None, name: str = "scripted") -> None:
        super().__init__(name, cache)
        self._replies = list(replies)
        self.prompts: list[Prompt] = []

    @property
    def description(self) -> str:
        return "scripted replies"

    @property
    def settings(self) -> dict[str, str | int]:
        return {"model": "scripted"}

    @property
    def price(self) -> Price:
        return SCRIPTED_PRICE

    def complete_prompt(self, prompt: Prompt) -> Completion:
        self.prompts.append(prompt)
        return Completion(self._replies.pop(0), "end_turn", Usage(100, 20), SECONDS_PER_CALL)
