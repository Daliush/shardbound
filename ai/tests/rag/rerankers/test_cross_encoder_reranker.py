from rag.chunks import Chunk
from rag.rerankers.cross_encoder_reranker import CrossEncoderReranker

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


class SharedWordsReranker(CrossEncoderReranker):
    """A cross-encoder reranker whose model is the fake, counting the models it loads."""

    def __init__(self, chunks: list[Chunk]) -> None:
        super().__init__("shared-words", chunks)
        self.loaded_models: list[FakeCrossEncoder] = []

    @property
    def description(self) -> str:
        return "reranked by the words shared with the question"

    def _load_model(self) -> FakeCrossEncoder:
        model = FakeCrossEncoder()
        self.loaded_models.append(model)
        return model


def test_reorders_every_candidate_the_highest_scored_first():
    reranked = SharedWordsReranker(CHUNKS).rerank_chunks(QUESTION, ["2.1", "8.2", "11.3.2"])

    assert reranked == ["11.3.2", "8.2", "2.1"]


def test_reads_only_the_candidates_texts_in_the_order_the_retriever_ranked_them():
    reranker = SharedWordsReranker(CHUNKS)

    reranker.rerank_chunks(QUESTION, ["8.2", "2.1"])

    assert reranker.loaded_models[0].given == [["[8. Effects] Destroy: sends a unit to the graveyard.",
                                                "[2. Deck building] A deck contains exactly 30 cards."]]


def test_loads_a_cross_encoder_in_float32_on_the_device_rag_device_names(sentence_transformers, monkeypatch):
    monkeypatch.setenv("RAG_DEVICE", "cuda")

    model = SharedWordsReranker(CHUNKS)._load_cross_encoder("org/model", prompts={"query": "Find it."})

    assert model.hugging_face_id == "org/model"
    assert model.settings == {"device": "cuda", "model_kwargs": {"dtype": "float32"}, "prompts": {"query": "Find it."}}


def test_loads_its_model_once_on_the_first_question_not_when_built():
    reranker = SharedWordsReranker(CHUNKS)
    assert reranker.loaded_models == []

    reranker.rerank_chunks(QUESTION, ["8.2"])
    reranker.rerank_chunks("Can I heal a unit?", ["8.4"])

    assert len(reranker.loaded_models) == 1
