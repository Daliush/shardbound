from rag.chunks import Chunk
from rag.rerankers.minilm.minilm_reranker import MiniLMReranker
from rag.rerankers.registry import build_reranker, list_reranker_names

CHUNKS = [Chunk("8.2", "rule", None, "[8. Effects] **Destroy**: sends a unit to the graveyard.", (), "r", "h")]


def test_names_every_reranker():
    assert list_reranker_names() == ["minilm-l6", "qwen3-reranker-0.6b"]


def test_builds_a_reranker_by_its_name_and_gives_it_that_name():
    reranker = build_reranker("minilm-l6", CHUNKS)
    assert isinstance(reranker, MiniLMReranker)
    assert reranker.name == "minilm-l6"
