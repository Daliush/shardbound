from rag.gate.retrieval_inputs import find_retrieval_changes


def test_finds_the_files_an_evaluation_depends_on_and_leaves_the_others_out():
    files = ["ai/rag/rerankers/minilm/minilm_reranker.py", "ai/uv.lock", "content/cards/ember/ash-warden.json",
             "docs/rules/08-effects.md", "engine/corpus/src/main/java/Chunk.java", "evals/datasets/v2/manifest.json",
             "evals/reports/v2/dev/2026-10-25T18-59-43Z.json", "ai/tests/rag/test_main.py", "ai/README.md",
             "frontend/src/app/lab/presentation.ts", "engine/core/src/test/java/RulesTest.java"]

    assert find_retrieval_changes(files) == files[:6]
