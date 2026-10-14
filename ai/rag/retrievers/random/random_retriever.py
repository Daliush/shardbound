"""The floor every retriever must beat: the chunks in a random order."""

import random

from rag.retrievers import Retriever


class RandomRetriever(Retriever):
    """Every chunk in a random order, the same order for a question at every run."""

    def __init__(self, chunk_ids: list[str], seed: int = 1) -> None:
        self._chunk_ids = list(chunk_ids)
        self._seed = seed

    def rank_chunks(self, question: str) -> list[str]:
        ranked = list(self._chunk_ids)
        random.Random(f"{self._seed}/{question}").shuffle(ranked)
        return ranked
