from rag.chunks import Chunk
from rag.rerankers.qwen3.qwen3_reranker import Qwen3Reranker

CHUNKS = [Chunk("8.2", "rule", None, "[8. Effects] Destroy: sends a unit to the graveyard.", (), "r", "h")]
INSTRUCTION = "Given a question about a card game's rules, retrieve the rules and cards needed to answer it."


def test_loads_its_pinned_model_in_float32_with_the_instruction_as_its_query_prompt(sentence_transformers):
    model = Qwen3Reranker("qwen3-reranker-0.6b", CHUNKS)._load_model()

    assert model.hugging_face_id == "Qwen/Qwen3-Reranker-0.6B"
    assert model.settings == {"device": "cpu", "model_kwargs": {"dtype": "float32"},
                              "revision": "e61197ed45024b0ed8a2d74b80b4d909f1255473",
                              "prompts": {"query": INSTRUCTION}}


def test_describes_itself_with_its_instruction():
    assert Qwen3Reranker("qwen3-reranker-0.6b", CHUNKS).description == (
        f'reranked by Qwen3-Reranker-0.6B, a cross-encoder built on an LLM, told "{INSTRUCTION}"')
