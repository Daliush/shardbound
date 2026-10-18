"""The rerankers: each reorders the candidates the steps before handed on, the most relevant first."""

from abc import abstractmethod

from rag.technique import Step, Technique


class Reranker(Technique):
    """A step after the retriever, its own layer, so that any reranker can follow any retriever."""

    step = Step.RERANK

    @abstractmethod
    def rerank_chunks(self, question: str, chunk_ids: list[str]) -> list[str]:
        """Every one of `chunk_ids`, the candidates handed on for `question`, reordered, the most relevant first: none
        is dropped, the prompt keeps the first 10."""

    def process(self, question: str, chunk_ids: list[str]) -> list[str]:
        return self.rerank_chunks(question, chunk_ids)
