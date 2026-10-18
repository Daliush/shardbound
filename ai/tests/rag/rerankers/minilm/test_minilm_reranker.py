import pytest

from rag.chunks import Chunk
from rag.rerankers.minilm.minilm_reranker import MiniLMReranker

CHUNKS = [
    Chunk("2.1", "rule", None, "[2. Deck building] A deck contains exactly 30 cards.", (), "r", "h1"),
    Chunk("8.4", "rule", None, "[8. Effects] Heal X: restores X defense to a unit.", (), "r", "h2"),
    Chunk("11.3.2", "rule", None, "[11.3 Anchor] A protected anchor unit cannot be destroyed.", (), "r", "h3"),
    Chunk("8.2", "rule", None, "[8. Effects] Destroy: sends a unit to the graveyard.", (), "r", "h4"),
]
QUESTION = "Can my anchor unit be destroyed?"


class FakeCrossEncoder:
    """Answers `rank` as sentence-transformers' CrossEncoder does, scoring a text by the words it shares with the
    question: no download."""

    def __init__(self) -> None:
        self.given: list[list[str]] = []

    def rank(self, question: str, texts: list[str]) -> list[dict]:
        self.given.append(list(texts))
        question_words = set(question.lower().rstrip("?").split())
        hits = []
        for position, text in enumerate(texts):
            hits.append({"corpus_id": position, "score": len(question_words & set(text.lower().split()))})
        return sorted(hits, key=lambda hit: hit["score"], reverse=True)


@pytest.fixture
def cross_encoder(monkeypatch) -> FakeCrossEncoder:
    fake = FakeCrossEncoder()
    monkeypatch.setattr(MiniLMReranker, "_model", fake)
    return fake


def test_reorders_every_candidate_the_highest_scored_first(cross_encoder):
    reranked = MiniLMReranker("minilm-l6", CHUNKS).rerank_chunks(QUESTION, ["2.1", "8.2", "11.3.2"])

    assert reranked == ["11.3.2", "8.2", "2.1"]


def test_reads_only_the_candidates_texts_in_the_order_the_retriever_ranked_them(cross_encoder):
    MiniLMReranker("minilm-l6", CHUNKS).rerank_chunks(QUESTION, ["8.2", "2.1"])

    assert cross_encoder.given == [["[8. Effects] Destroy: sends a unit to the graveyard.",
                                    "[2. Deck building] A deck contains exactly 30 cards."]]


def test_describes_itself():
    assert MiniLMReranker("minilm-l6", CHUNKS).description == (
        "reranked by MiniLM-L6, a cross-encoder that reads the question with each candidate")
