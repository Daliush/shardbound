"""The retrievers: each ranks the chunk ids for a question, the most relevant first."""

from abc import abstractmethod

from rag.technique import Step, Technique

CANDIDATE_COUNT = 20
"""How many chunks of a retriever's ranking the next step reads, the top ~20 of design doc §5.2: a reranker reads each
with the question, too slow for the whole corpus."""


class Retriever(Technique):
    """The first step: it starts from the question alone."""

    step = Step.RETRIEVER

    @abstractmethod
    def rank_chunks(self, question: str) -> list[str]:
        """The ids of the chunks it ranks for `question`, the most relevant first, each id once."""

    def process(self, question: str, chunk_ids: list[str]) -> list[str]:
        return self.rank_chunks(question)
