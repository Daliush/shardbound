"""Every generator by name, as the command line knows them."""

from collections.abc import Callable

from arbiter.generation import Generator
from arbiter.generation.claude.claude_generator import ClaudeGenerator
from arbiter.generation.completion_cache import CompletionCache

GENERATOR_FACTORIES: dict[str, Callable[[str, CompletionCache | None], Generator]] = {
    "claude-haiku-5.5": lambda name, cache: ClaudeGenerator(name, "claude-haiku-5-5", "medium", cache),
}
"""Name → how to build it from its name and the cache of its completions."""

BASELINE_GENERATOR = "claude-haiku-5.5"
"""The generator the Arbiter answers with: the `api+rag` configuration (design doc §6.2)."""


def list_generator_names() -> list[str]:
    return list(GENERATOR_FACTORIES)


def build_generator(name: str, cache: CompletionCache | None = None) -> Generator:
    return GENERATOR_FACTORIES[name](name, cache)
