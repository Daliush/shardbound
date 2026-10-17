"""
Every retriever by name, as the evaluation and the command line know them. Each family lists its own variants in its
package: the BM25 settings in `bm25/bm25_variants.py`, the dense models in `dense/embedding_models.py`. A fusion
names the retrievers it fuses, and an expansion the retriever it wraps, built as they are built alone.
"""

from collections.abc import Callable

from rag.chunks import Chunk
from rag.repository.database import connect_database
from rag.repository.embedding_repository import EmbeddingRepositoryPostgres
from rag.retrievers import Retriever
from rag.retrievers.bm25.bm25_retriever import Bm25Retriever
from rag.retrievers.bm25.bm25_variants import BM25_VARIANTS
from rag.retrievers.dense.dense_retriever import DenseRetriever
from rag.retrievers.dense.embedding_models import LOCAL_EMBEDDING_MODELS, LocalEmbeddingModel
from rag.retrievers.expansion.expansion_retriever import ExpansionRetriever
from rag.retrievers.fusion.rrf_retriever import RrfRetriever
from rag.retrievers.random.random_retriever import RandomRetriever

RETRIEVER_FACTORIES: dict[str, Callable[[list[Chunk]], Retriever]] = {
    "random": lambda chunks: RandomRetriever([chunk.id for chunk in chunks]),
    **{name: (lambda chunks, settings=settings: Bm25Retriever(chunks, settings))
       for name, settings in BM25_VARIANTS.items()},
    **{f"dense-{model_name}": (lambda chunks, model_name=model_name:
                               DenseRetriever(LocalEmbeddingModel(model_name),
                                              EmbeddingRepositoryPostgres(connect_database())))
       for model_name in LOCAL_EMBEDDING_MODELS},
    "rrf-bm25-dense": lambda chunks: RrfRetriever([build_retriever("bm25", chunks),
                                                   build_retriever("dense-qwen3-0.6b", chunks)]),
    "rrf-bm25-dense-expanded": lambda chunks: ExpansionRetriever(build_retriever("rrf-bm25-dense", chunks), chunks),
}
"""Name → how to build it from the chunks. A retriever is built only when it is evaluated: a dense one loads its
model."""


def list_retriever_names() -> list[str]:
    return list(RETRIEVER_FACTORIES)


def build_retriever(name: str, chunks: list[Chunk]) -> Retriever:
    return RETRIEVER_FACTORIES[name](chunks)
