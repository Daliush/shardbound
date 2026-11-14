from rag.contexts import ContextBuilder
from rag.expanders import Expander
from rag.rerankers import Reranker


class ReversingReranker(Reranker):
    """Reverses the candidates it is handed, and keeps them to show what it read."""

    def __init__(self, name: str = "reversing") -> None:
        super().__init__(name)
        self.given: list[list[str]] = []

    @property
    def description(self) -> str:
        return "reversed"

    def rerank_chunks(self, question: str, chunk_ids: list[str]) -> list[str]:
        self.given.append(list(chunk_ids))
        return list(reversed(chunk_ids))


class CardAddingExpander(Expander):
    """Puts Tempest's card before the chunks it is handed."""

    def __init__(self, name: str = "card") -> None:
        super().__init__(name)

    @property
    def description(self) -> str:
        return "with Tempest's card"

    def expand_chunks(self, question: str, chunk_ids: list[str]) -> list[str]:
        return ["neutral.tempest", *chunk_ids]


class FirstChunkContext(ContextBuilder):
    """Keeps the first chunk it is handed, then adds rule 11.3.3."""

    def __init__(self, name: str = "first-chunk") -> None:
        super().__init__(name)

    @property
    def description(self) -> str:
        return "cut to its first chunk, then rule 11.3.3"

    def build_context(self, question: str, ranked_chunk_ids: list[str]) -> list[str]:
        return [*ranked_chunk_ids[:1], "11.3.3"]
