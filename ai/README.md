# Shardbound AI

The Python side of the project: the code that calls or evaluates models (design doc §3.1). It starts with the Arbiter's evaluation dataset (`dataset/`), which adds to the engine's questions the ones the engine cannot write; the RAG (`rag/`) and the eval harness come next, in the same `uv` project.

```bash
# from ai/
uv run pytest
```

The unanswerable questions (`dataset/unanswerable.py`) need no model: invented keywords, cards and mechanics, checked absent from the catalog and the rulebook, and the open points `docs/rules/12-open-points.md` still lists. Their answer key is an abstention.
