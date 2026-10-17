"""The retrievers: each ranks the chunk ids for a question, the most relevant first."""

from abc import ABC, abstractmethod


class Retriever(ABC):
    """What every retriever does."""

    @property
    @abstractmethod
    def description(self) -> str:
        """What it does, written from its settings for whoever reads an evaluation: a phrase another retriever's
        description can hold, such as "RRF (k = 60) of BM25 (…) and dense retrieval (…)"."""

    @abstractmethod
    def rank_chunks(self, question: str) -> list[str]:
        """The ids of the chunks it ranks for `question`, the most relevant first, each id once."""
