"""The refs expansion (design doc §5.2): the candidates the reranker receives."""

import re

from rag.chunks import Chunk
from rag.retrievers import Retriever


class ExpansionRetriever(Retriever):
    """
    Wraps a retriever of the same chunks. The candidates are the chunks of the cards the question names, then the
    first `top_k` chunks the retriever ranks, then the chunks all of these refer to, one level deep, each chunk once.
    A question that names a card shares no word with the rules the card relies on: the card's refs bring them. Only
    cards are found by name: a term's name ("destroy", "die") is in most questions.
    """

    def __init__(self, retriever: Retriever, chunks: list[Chunk], top_k: int = 20) -> None:
        self._retriever = retriever
        self._top_k = top_k
        """How many chunks of the wrapped retriever's ranking become candidates, before the cards and the refs join
        them."""
        self._refs_by_chunk_id: dict[str, tuple[str, ...]] = {}
        self._card_id_by_lowercase_name: dict[str, str] = {}
        for chunk in chunks:
            self._refs_by_chunk_id[chunk.id] = chunk.refs
            if chunk.kind == "card":
                self._card_id_by_lowercase_name[chunk.name.lower()] = chunk.id
        if not self._card_id_by_lowercase_name:
            # With no name to look for, the pattern would match the empty string everywhere.
            raise LookupError("No card among the chunks: build the index with `uv run python -m rag index`")
        self._card_name_pattern = self._compile_card_name_pattern()

    @property
    def description(self) -> str:
        return (f"refs expansion, one level deep, of the cards the question names and the top {self._top_k} of "
                f"{self._retriever.description}")

    def rank_chunks(self, question: str) -> list[str]:
        retrieved = self._find_named_cards(question) + self._retriever.rank_chunks(question)[:self._top_k]
        return list(dict.fromkeys(retrieved + self._follow_refs(retrieved)))

    def _compile_card_name_pattern(self) -> re.Pattern[str]:
        """Every lowercase card name as a whole word. The longest come first, so where a name starts with a shorter
        one, the longer is found."""
        alternatives = []
        for name in sorted(self._card_id_by_lowercase_name, key=len, reverse=True):
            alternatives.append(re.escape(name))
        return re.compile(r"\b(?:" + "|".join(alternatives) + r")\b")

    def _find_named_cards(self, question: str) -> list[str]:
        """The cards the question names, whatever its case, in the order it names them. The question is lowercased
        rather than matched ignoring case, so that every match is a lowercase name."""
        card_ids = []
        for match in self._card_name_pattern.finditer(question.lower()):
            card_ids.append(self._card_id_by_lowercase_name[match.group()])
        return card_ids

    def _follow_refs(self, chunk_ids: list[str]) -> list[str]:
        """The chunks these refer to, in their order: refs of refs are not followed."""
        refs = []
        for chunk_id in chunk_ids:
            refs.extend(self._refs_by_chunk_id[chunk_id])
        return refs
