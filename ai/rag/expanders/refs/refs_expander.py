"""The refs expansion (design doc §5.2): the candidates the reranker receives."""

from rag.chunks import Chunk
from rag.expanders import Expander
from rag.named_cards import NamedCardFinder


class RefsExpander(Expander):
    """
    The chunks of the cards the question names, then the chunks it is handed, then the chunks all of these refer to,
    one level deep, each chunk once. A question that names a card shares no word with the rules the card relies on:
    the card's refs bring them.
    """

    def __init__(self, name: str, chunks: list[Chunk]) -> None:
        super().__init__(name)
        self._refs_by_chunk_id: dict[str, tuple[str, ...]] = {}
        for chunk in chunks:
            self._refs_by_chunk_id[chunk.id] = chunk.refs
        self._named_cards = NamedCardFinder(chunks)

    @property
    def description(self) -> str:
        return "expanded with the cards the question names and the refs of all of them, one level deep"

    def expand_chunks(self, question: str, chunk_ids: list[str]) -> list[str]:
        retrieved = self._named_cards.find_named_cards(question) + chunk_ids
        return list(dict.fromkeys(retrieved + self._follow_refs(retrieved)))

    def _follow_refs(self, chunk_ids: list[str]) -> list[str]:
        """The chunks these refer to, in their order: refs of refs are not followed."""
        refs = []
        for chunk_id in chunk_ids:
            refs.extend(self._refs_by_chunk_id[chunk_id])
        return refs
