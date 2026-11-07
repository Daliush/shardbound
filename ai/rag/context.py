"""The context: the chunks the RAG hands the Arbiter for a question, the ones its prompt holds (design doc §5.2)."""

from rag.chunks import Chunk
from rag.named_cards import NamedCardFinder

PROMPT_SIZE = 10
"""How many ranked chunks the prompt holds: a chunk is about 40 tokens, and no question of the dataset needs more than
10 rules."""


class ContextSelector:
    """
    The first `PROMPT_SIZE` chunks of the pipeline's ranking, then the cards the question names that are not among
    them. The reranker can rank a card the question names past the cut, and the Arbiter cannot rule on a card it does
    not read.
    """

    def __init__(self, chunks: list[Chunk], size: int = PROMPT_SIZE) -> None:
        self._named_cards = NamedCardFinder(chunks)
        self._size = size

    def select_context(self, question: str, ranked_chunk_ids: list[str]) -> list[str]:
        """The chunk ids of the context, each once, in the order the pipeline ranked them, the named cards it ranked
        past the cut last."""
        context = ranked_chunk_ids[:self._size]
        for card_id in self._named_cards.find_named_cards(question):
            if card_id not in context:
                context.append(card_id)
        return context
