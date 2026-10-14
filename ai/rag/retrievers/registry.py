"""
Every retriever by name, as the evaluation and the command line know them. Each family lists its own variants in its
package: the BM25 settings in `bm25/bm25_variants.py`.
"""

from collections.abc import Callable

from rag.chunks import Chunk
from rag.retrievers import Retriever
from rag.retrievers.bm25.bm25_retriever import Bm25Retriever
from rag.retrievers.bm25.bm25_variants import BM25_VARIANTS
from rag.retrievers.random.random_retriever import RandomRetriever

RETRIEVER_FACTORIES: dict[str, Callable[[list[Chunk]], Retriever]] = {
    "random": lambda chunks: RandomRetriever([chunk.id for chunk in chunks]),
    **{name: (lambda chunks, settings=settings: Bm25Retriever(chunks, settings))
       for name, settings in BM25_VARIANTS.items()},
}
"""Name → how to build it from the chunks. A retriever is built only when it is evaluated."""


def list_retriever_names() -> list[str]:
    return list(RETRIEVER_FACTORIES)


def build_retriever(name: str, chunks: list[Chunk]) -> Retriever:
    return RETRIEVER_FACTORIES[name](chunks)
