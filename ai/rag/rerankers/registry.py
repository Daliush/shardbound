"""
Every reranker by name, as the evaluation and the command line know them. A reranker follows any retriever: the
run's layers name both.
"""

from collections.abc import Callable

from rag.chunks import Chunk
from rag.rerankers import Reranker
from rag.rerankers.minilm.minilm_reranker import MiniLMReranker
from rag.rerankers.qwen3.qwen3_reranker import Qwen3Reranker

RERANKER_FACTORIES: dict[str, Callable[[str, list[Chunk]], Reranker]] = {
    "minilm-l6": MiniLMReranker,
    "qwen3-reranker-0.6b": Qwen3Reranker,
}
"""Name → how to build it from its name and the chunks, whose texts it reads. Its model loads on the first question
it reranks."""


def list_reranker_names() -> list[str]:
    return list(RERANKER_FACTORIES)


def build_reranker(name: str, chunks: list[Chunk]) -> Reranker:
    return RERANKER_FACTORIES[name](name, chunks)
