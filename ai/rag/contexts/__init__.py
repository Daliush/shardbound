"""The contexts: each chooses, in the chunks the steps before handed on, the ones the prompt holds (design doc
§5.2)."""

from abc import abstractmethod

from rag.technique import Step, Technique

PROMPT_SIZE = 10
"""How many ranked chunks the prompt holds: a chunk is about 40 tokens, and no question of the dataset needs more than
10 rules."""


class ContextBuilder(Technique):
    """The last step, its own layer: what the RAG hands the Arbiter for a question. What it returns is not cut again:
    the prompt holds all of it."""

    step = Step.CONTEXT

    @abstractmethod
    def build_context(self, question: str, ranked_chunk_ids: list[str]) -> list[str]:
        """The chunk ids of the context for `question`, each once, chosen from the ranking the steps before handed
        on."""

    def process(self, question: str, chunk_ids: list[str]) -> list[str]:
        return self.build_context(question, chunk_ids)
