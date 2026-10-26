from rag.chunks import Chunk
from rag.rerankers.minilm.minilm_reranker import MiniLMReranker

CHUNKS = [Chunk("8.2", "rule", None, "[8. Effects] Destroy: sends a unit to the graveyard.", (), "r", "h")]


def test_loads_its_pinned_model_in_float32_on_the_cpu_by_default(sentence_transformers):
    model = MiniLMReranker("minilm-l6", CHUNKS)._load_model()

    assert model.hugging_face_id == "cross-encoder/ms-marco-MiniLM-L6-v2"
    assert model.settings == {"device": "cpu", "model_kwargs": {"dtype": "float32"},
                              "revision": "233902d25c440f23af6f7d6e94d2946bac0bee0a"}


def test_describes_itself():
    assert MiniLMReranker("minilm-l6", CHUNKS).description == (
        "reranked by MiniLM-L6, a cross-encoder that reads the question with each candidate")
