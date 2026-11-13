"""Every context by name, as the evaluation and the command line know them. A context follows any pipeline."""

from collections.abc import Callable

from rag.chunks import Chunk
from rag.contexts import ContextBuilder
from rag.contexts.named_cards.named_cards_context import NamedCardsContext

CONTEXT_FACTORIES: dict[str, Callable[[str, list[Chunk]], ContextBuilder]] = {
    "top10-plus-named-cards": lambda name, chunks: NamedCardsContext(name, chunks, follow_refs=False),
    "top10-plus-named-cards-refs": lambda name, chunks: NamedCardsContext(name, chunks, follow_refs=True),
}
"""Name → how to build it from its name and the chunks, whose names and refs it reads."""


def list_context_names() -> list[str]:
    return list(CONTEXT_FACTORIES)


def build_context(name: str, chunks: list[Chunk]) -> ContextBuilder:
    return CONTEXT_FACTORIES[name](name, chunks)
