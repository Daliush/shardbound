"""The context of the first ranked chunks and the cards the question names (design doc §5.2)."""

from rag.chunks import Chunk
from rag.contexts import PROMPT_SIZE, ContextBuilder
from rag.named_cards import NamedCardFinder


class NamedCardsContext(ContextBuilder):
    """
    The first `size` chunks of the ranking, then the cards the question names that are not among them: the reranker
    can rank a card the question names past the cut, and the Arbiter cannot rule on a card it does not read. With
    `follow_refs`, then the chunks these cards refer to, one level deep: the reranker ranks the rules a card relies on
    low, since they share no word with a question that names the card, and the cut drops them.
    """

    def __init__(self, name: str, chunks: list[Chunk], follow_refs: bool, size: int = PROMPT_SIZE) -> None:
        super().__init__(name)
        self._refs_by_chunk_id: dict[str, tuple[str, ...]] = {}
        for chunk in chunks:
            self._refs_by_chunk_id[chunk.id] = chunk.refs
        self._named_cards = NamedCardFinder(chunks)
        self._follow_refs = follow_refs
        self._size = size

    @property
    def description(self) -> str:
        description = f"cut to the first {self._size} chunks, then the cards the question names"
        if self._follow_refs:
            description += " and the chunks these cards refer to, one level deep"
        return description

    def build_context(self, question: str, ranked_chunk_ids: list[str]) -> list[str]:
        """The first chunks in the order they were ranked, then the named cards ranked past the cut, in the order the
        question names them, then each card's refs, in the card's order."""
        named_cards = self._named_cards.find_named_cards(question)
        added = list(named_cards)
        if self._follow_refs:
            for card_id in named_cards:
                added.extend(self._refs_by_chunk_id[card_id])
        return list(dict.fromkeys(ranked_chunk_ids[:self._size] + added))
