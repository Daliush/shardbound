from rag.chunks import Chunk
from rag.retrievers.bm25.bm25_retriever import Bm25Retriever
from rag.retrievers.bm25.bm25_variants import BM25_VARIANTS
from rag.retrievers.random.random_retriever import RandomRetriever
from rag.retrievers.registry import build_retriever, list_retriever_names

CHUNKS = [Chunk("8.2", "rule", "[8. Effects] **Destroy**: sends a unit to the graveyard.", (), "r", "h")]


def test_names_random_then_every_bm25_variant():
    assert list_retriever_names() == ["random", *BM25_VARIANTS]


def test_builds_a_retriever_by_its_name():
    assert isinstance(build_retriever("random", CHUNKS), RandomRetriever)
    assert isinstance(build_retriever("bm25-no-stemming", CHUNKS), Bm25Retriever)
