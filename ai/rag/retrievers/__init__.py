"""The retrievers: each ranks the chunk ids for a question, the most relevant first."""

from abc import ABC, abstractmethod


class Retriever(ABC):
    """What every retriever does."""

    @abstractmethod
    def rank_chunks(self, question: str) -> list[str]:
        """The ids of the chunks it ranks for `question`, the most relevant first, each id once."""
