"""Every expander by name, as the evaluation and the command line know them. An expander follows any retriever."""

from collections.abc import Callable

from rag.chunks import Chunk
from rag.expanders import Expander
from rag.expanders.refs.refs_expander import RefsExpander

EXPANDER_FACTORIES: dict[str, Callable[[str, list[Chunk]], Expander]] = {
    "refs": RefsExpander,
}
"""Name → how to build it from its name and the chunks, whose names and refs it reads."""


def list_expander_names() -> list[str]:
    return list(EXPANDER_FACTORIES)


def build_expander(name: str, chunks: list[Chunk]) -> Expander:
    return EXPANDER_FACTORIES[name](name, chunks)
