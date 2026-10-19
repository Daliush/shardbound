---
name: shardbound-rag
description: How Shardbound's RAG Arbiter is built and how to work on it with the maintainer — the step-by-step way of working, the pipeline and where each step's code lives, the chunks (rules, vocabulary terms, cards, refs), the retrieval metrics (relevant chunks = citations, Recall@10 / Recall@20, MRR, candidates, the recall curve, the random floor, dev noise), the contracts and registries of the retrievers (BM25, dense retrieval with pgvector, RRF), the refs expansion and the rerankers, the rules the code keeps, and where the decisions and open points are written. Use this whenever you touch engine/corpus or ai/rag, add or tune a retriever, an embedding model, the reranker or the prompt, read or explain retrieval numbers, or explain a RAG concept to the maintainer — even for a quick question about a chunk or a metric.
---

# The RAG Arbiter

- What the pipeline is: `docs/design.md` §5.2 (the pipeline), §6.2 (the variants), §6.3 (the metrics). When this skill and the design disagree, the design wins: fix the skill.
- Why each choice was made, what was rejected, and what is still open: `docs/decisions/rag.md`.
- Where it stands: the Status section of `AGENTS.md`.
- The numbers: the latest evaluation in `evals/reports/<version>/dev/`.

## How we work on it

The maintainer must be able to explain every brick of the RAG, so it is built one step at a time, together:

1. Explain the idea in a few lines, on real data: a real chunk, a real dev question, the real numbers. Tables over prose. When the maintainer checks their understanding, say exactly what is right and what is wrong.
2. For a design, answer their questions and show pseudo-code before coding. Then code it, run it, and show the numbers with one or two traced examples: a question, what came back, and why.
3. Commit only once they have read it and have no question left. Then push; they open and merge the PR themselves, so give them a ready title and description.

After a refactor, show that the output is unchanged: the same chunks, the same scores.

## The pipeline

```
question ─┬→ BM25                 → list A ┐
          └→ embedding → cosine   → list B ┘→ RRF (k = 60) → top 20
named cards + top 20 → + refs (one level) → cross-encoder reranker → top 10 → prompt
```

| Step | Techniques | Code |
|---|---|---|
| Chunks, with their refs | | `engine/corpus` (Java), described in `engine/corpus/README.md` |
| Retriever | random (the floor), BM25 and its variants, dense, RRF | `ai/rag/retrievers/` |
| Expand | the cards a question names, then the refs | `ai/rag/expanders/refs/` |
| Rerank | MiniLM-L6, a cross-encoder | `ai/rag/rerankers/` |
| Answer | the prompt and its output `{answer, citations, answerable}` | not built yet |
| Evaluation | the tree of every pipeline, scored and saved | `ai/rag/evaluation/` |

## Words that matter

| Word | Meaning here |
|---|---|
| **chunk** | one rule, one vocabulary term or one card; only its `text` is embedded, indexed and shown to the model |
| **refs** | links from a chunk to the chunks it relies on, computed at indexing (11.3.5 → 5.4.3). Used after retrieval, to bring those chunks along |
| **citations** | a field of each **dataset question**: what a correct answer must cite (the engine's rules, or the card of a factual question). The Arbiter's output has a `citations` field too |
| **relevant chunks** | a question's `citations`; one citation = one chunk id |
| **Recall@k** | the share of a question's relevant chunks among the first k ranked. Decides between pipelines |
| **RR / MRR** | 1 / rank of the first relevant chunk, no cutoff; MRR is its mean. Explains, does not decide |
| **lexical / sparse** | BM25: exact words, no model |
| **dense / semantic** | embeddings: a model turns each text into a vector, cosine compares them |
| **bi-encoder / cross-encoder** | the embedding model encodes question and chunk apart (fast, every chunk); the reranker reads them together (precise, few candidates) |
| **query marker** | text some embedding models want before the question (Qwen3: an instruction), added at the encode call, never stored |

## The code

`ai/rag` has one package per responsibility, so that anything is found at a glance:

- `__main__.py` only parses the arguments, calls a service and prints. `indexing/` and `evaluation/` hold one service each, one per command.
- `technique.py` holds `Step` (retriever, expand, rerank; answer later) and `Technique`, which `Retriever`, `Expander` and `Reranker` extend. Each family has its contract, a `registry.py` that names each technique, and one sub-package per technique: `retrievers/bm25/`, `retrievers/dense/`, `retrievers/fusion/`, `expanders/refs/`, `rerankers/minilm/`.
- `repository/` is where the RAG keeps things: one repository per table, a contract and its `…Postgres` implementation, with `…InMemory` fakes in the tests, and `evaluation_repository.py`, which writes the evaluation files (a bucket later).
- `evaluation/`: `evaluation_service.py` builds the tree of pipelines, `stages.py` its nodes, `retrieval_metrics.py` the metrics, `evaluation_json.py` the saved document.

Rules the code keeps:

- A contract is an `ABC` that a class extends explicitly, as in Java, not a `Protocol`: the contract shows in the class line.
- A variant is one more entry in its family's registry, and changes **one** setting.
- Method names say what they do, verb first. Loops are explicit `for` loops, not comprehensions. Never pass two lists that only line up by position (no `zip`): make one object per item where it is produced, as `EmbeddingModel.embed_chunk(chunk)` returns a `ChunkEmbedding`.
- Name for the future: what the answer evaluation will share stays generic (`Question`, `Scores`, `Report`, `evaluate`); only what really is retrieval says so (`retrieval_metrics.py`).
- Every technique reads the chunks from the database, never from `ai/.corpus/chunks.jsonl`, so a run cannot mix two versions of the corpus. The evaluation reads them once; a technique makes no database call per question.
- The unit tests download no model and start no database: they use fakes (`tests/rag/retrievers/dense/conftest.py`, and a monkeypatched `_model` for the reranker). A real index and evaluation prove the SQL and the models.

## The evaluation

`uv run python -m rag evaluate --description "<why>"`, from `ai/`, scores every pipeline the techniques make; `scripts/evaluate.sh` builds the index first (the `shardbound-pipeline-run` skill says how to run and check it).

- A question's relevant chunks are its `citations`. Unanswerable questions have none and are left out: 61 of the 72 dev questions count.
- **Recall@10** decides: it is what the prompt holds. **Recall@20** is the top 20 that the expansion starts from. **MRR** explains. **Candidates** is how many chunks a run ranks. The **recall curve**, Recall@k for every k, compares runs at the same number of chunks; its last value is the most a reranker could keep.
- One question moves a score by about 1.6 points, and a 7-question category is noise: dev gives a direction, test (596 questions) the published numbers. `--split test` never chooses anything.
- The random retriever is the floor: every pipeline must beat it.
- Each evaluation is one new file, `evals/reports/<version>/<split>/<timestamp>.json`, never overwritten. It holds the code commit (`-dirty` when a tracked file outside `evals/reports/` changed), why it was run, and each run named by its layers, with its scores and each question's ranking. The game server serves it as written (`GET /api/evaluations/…`).

## What each step keeps

- **Chunks** (`engine/corpus`): a rule is prefixed with the titles of its sections, read from its ID, and keeps the rulebook's Markdown; the ID itself stays out of the text. A card is a stats line, then `CardTextRenderer`'s text. A rule's refs are the numbers its text writes; a card's are read from its data (`CardRefs`, whose switches have no default), and a section number becomes all its rules. A test fails on a ref to nothing, and `DatasetCitationsTest` on a citation with no chunk.
- **BM25**: the baseline keeps stop words, which bm25s removes by default.
- **Dense retrieval**: one `LocalEmbeddingModel(name)` per entry of `LOCAL_EMBEDDING_MODELS`; the model adds its own query marker at the encode call. Only the chunks whose text changed are embedded again. A change to an entity means `docker compose down -v`: the tables are never altered.
- **RRF**: a chunk scores Σ 1 / (60 + rank) over the rankings it is in; on a tie, the first rankings' order wins.
- **Refs expansion**: the candidates, each once, are the chunks of the cards the question names, then the top 20 it is handed, then the refs of all of these, one level deep. Card names are matched exactly, as whole words, on the lowercased question, the longest first, never with `re.IGNORECASE`. Vocabulary terms are never matched.
- **Reranker**: `rerank_chunks(question, chunk_ids)` returns every candidate reordered, none dropped; the prompt keeps the first 10. It reads the candidates, never the corpus, and loads its model on the first question.

## Docs to keep in sync

`docs/design.md` §5.2, §6.2, §6.3 and its changelog; `docs/decisions/rag.md` for each decision taken with the maintainer; the status and commands of `AGENTS.md`; `ai/README.md`; `engine/corpus/README.md`; this skill when the code's layout or its rules change.
