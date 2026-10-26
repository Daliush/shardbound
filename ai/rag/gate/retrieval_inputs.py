"""What an evaluation's numbers depend on: a change to any of these files calls for a new evaluation."""

RETRIEVAL_INPUTS = ("ai/rag/", "ai/pyproject.toml", "ai/uv.lock", "engine/core/src/main/", "engine/corpus/",
                    "content/", "docs/rules/", "evals/datasets/")
"""The code that retrieves and scores and its packages, the engine and the corpus that cut the chunks, the content and
the rules they are cut from, and the datasets they are scored on. `evals/reports/` is the output, never an input."""


def find_retrieval_changes(files: list[str]) -> list[str]:
    """The files among `files`, paths from the repository's root, that an evaluation's numbers depend on."""
    changes = []
    for file in files:
        if file.startswith(RETRIEVAL_INPUTS):
            changes.append(file)
    return changes
