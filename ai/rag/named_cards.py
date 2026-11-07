"""The cards a question names: the refs expansion adds them to the candidates, the context to the prompt."""

import re

from rag.chunks import Chunk


class NamedCardFinder:
    """
    Finds the cards a question names, by their exact names, as whole words, whatever their case. Only cards are found
    by name: a term's name ("destroy", "die") is in most questions.
    """

    def __init__(self, chunks: list[Chunk]) -> None:
        self._card_id_by_lowercase_name: dict[str, str] = {}
        for chunk in chunks:
            if chunk.kind == "card":
                self._card_id_by_lowercase_name[chunk.name.lower()] = chunk.id
        if not self._card_id_by_lowercase_name:
            # With no name to look for, the pattern would match the empty string everywhere.
            raise LookupError("No card among the chunks: build the index with `uv run python -m rag index`")
        self._card_name_pattern = self._compile_card_name_pattern()

    def find_named_cards(self, question: str) -> list[str]:
        """The cards the question names, in the order it names them. The question is lowercased rather than matched
        ignoring case, so that every match is a lowercase name."""
        card_ids = []
        for match in self._card_name_pattern.finditer(question.lower()):
            card_ids.append(self._card_id_by_lowercase_name[match.group()])
        return card_ids

    def _compile_card_name_pattern(self) -> re.Pattern[str]:
        """Every lowercase card name as a whole word. The longest come first, so where a name starts with a shorter
        one, the longer is found."""
        alternatives = []
        for name in sorted(self._card_id_by_lowercase_name, key=len, reverse=True):
            alternatives.append(re.escape(name))
        return re.compile(r"\b(?:" + "|".join(alternatives) + r")\b")
