from arbiter.generation import Generator
from arbiter.generation.completion import Completion, Usage
from arbiter.generation.completion_cache import CompletionCache
from arbiter.generation.prompt import Prompt

VALID_REPLY = '{"answer": "Ash Warden is destroyed.", "citations": ["8.2"], "answerable": true}'


class ScriptedGenerator(Generator):
    """Replies with the texts it is given, one per call, and keeps the prompts it read: the model, in tests."""

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

    def complete_prompt(self, prompt: Prompt) -> Completion:
        self.prompts.append(prompt)
        return Completion(self._replies.pop(0), "end_turn", Usage(100, 20))
