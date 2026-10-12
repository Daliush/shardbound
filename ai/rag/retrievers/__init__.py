"""The retrievers: each ranks the chunk ids for a question, the most relevant first."""

from typing import Protocol


class Retriever(Protocol):

    def rank_chunks(self, question: str) -> list[str]:
        """The ids of the chunks it ranks for `question`, the most relevant first."""
        ...
