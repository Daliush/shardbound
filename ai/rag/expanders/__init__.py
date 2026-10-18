"""The expanders: each adds to the chunks a retriever handed on the chunks they lead to."""

from abc import abstractmethod

from rag.technique import Step, Technique


class Expander(Technique):
    """A step between the retriever and the reranker, its own layer, so that any retriever can be expanded or not."""

    step = Step.EXPAND

    @abstractmethod
    def expand_chunks(self, question: str, chunk_ids: list[str]) -> list[str]:
        """The chunks handed on for `question` with the ones it adds, each id once."""

    def process(self, question: str, chunk_ids: list[str]) -> list[str]:
        return self.expand_chunks(question, chunk_ids)
