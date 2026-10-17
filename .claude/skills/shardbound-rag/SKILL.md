---
name: shardbound-rag
description: How Shardbound's RAG Arbiter is built and how to work on it with the maintainer — the step-by-step plan and where it stands, the chunks engine/corpus writes (rules, vocabulary terms, cards, refs), the retrieval metrics of ai/rag (relevant chunks = citations, Recall@10 / Recall@20, MRR, the candidate recall and count, the random floor, dev noise), the retriever interface and its registry of variants (random, BM25 and its settings, dense retrieval with pgvector, the RRF fusion, the card names and refs expansion), the decisions already settled and the points still open. Use this whenever you touch engine/corpus or ai/rag, add or tune a retriever, an embedding model, the reranker or the prompt, read or explain retrieval numbers, or explain a RAG concept to the maintainer — even for a quick question about a chunk or a metric.
---

# The RAG Arbiter

Design: `docs/design.md` §5.2 (the pipeline), §6.2 (the variants), §6.3 (the metrics). The design wins when this skill and it disagree; fix this skill.

## How we work on it

The maintainer wants to understand every brick of the RAG: it is the part of the portfolio he must be able to explain. So the RAG is built **one step at a time, together**:

1. Explain the idea in a few lines, on **real data**: a real chunk, a real dev question, the real numbers. Tables over prose. When he checks his understanding, say exactly what is right and what is wrong.
2. Code it, run it, show the numbers and one or two traced examples (a question, what came back, why).
3. Commit only once he has read it and has no question left. Then push; he opens and merges the PR himself, so give him a ready title and description (ending with the Claude Code line). `gh` is installed but not signed in.

He reviews code closely. Method names must say what they do without reading the body, verb first; no static entry point that builds a hidden-state instance; one package per step with one public entry class; code that follows the model he states ("first pass collects the refs, second pass expands the sections"). After a refactor, show the output is unchanged (byte-identical chunks, same scores).

He writes in French or English: answer in his language; everything committed is in English.

## The plan

```
question ─┬→ card names found     → their card chunks                       (step 6)
          ├→ BM25                 → list A ┐                                (step 3)
          └→ embedding → cosine   → list B ┘→ RRF (k = 60) → top ~20        (steps 4, 5)
named cards + top ~20 → + refs (one level) → cross-encoder reranker → top 10 → prompt   (steps 6, 7, 8)
```

| Step | What | Where | State |
|---|---|---|---|
| 1 | Chunks with their refs | `engine/corpus` (Java) | done |
| 2 | Retrieval metrics, random floor | `ai/rag/evaluation` | done |
| 3 | BM25 and its variants | `ai/rag/retrievers/bm25_retriever.py` | done |
| 4 | Dense retrieval: embeddings in pgvector, cosine | `ai/rag` + Postgres in `compose.yaml` | done |
| 5 | RRF fusion of BM25 and dense retrieval | `ai/rag/retrievers/fusion/` | done |
| 6 | Card names found in the question, refs expansion | `ai/rag/retrievers/expansion/` | done |
| 7 | Cross-encoder reranker | `ai/rag` | next |
| 8 | Prompt and structured output `{answer, citations, answerable}` | `ai/rag` | |

Then the eval harness (§6.3: key facts, rules cited, step order, abstention, format, judge) and the benchmark explorer (§8.2). The served pipeline and the evaluated one are the same code: later, the Angular client calls the Java backend, which forwards to a Python FastAPI service calling the same retrievers. Users never call Python directly.

## Words that matter

| Word | Meaning here |
|---|---|
| **chunk** | one rule, one vocabulary term or one card; only its `text` is embedded, indexed and shown to the model |
| **refs** | links from a chunk to the chunks it relies on, computed at indexing (11.3.5 → 5.4.3). Used after retrieval, to bring those chunks along |
| **citations** | a field of each **dataset question**: what a correct answer must cite (the engine's rules, or the card of a factual question). The Arbiter's output has a `citations` field too |
| **relevant chunks** | a question's `citations`; one citation = one chunk id |
| **Recall@k** | the share of a question's relevant chunks among the first k ranked. Decides between retrievers |
| **RR / MRR** | 1 / rank of the first relevant chunk, no cutoff; MRR is its mean. Explains, does not decide |
| **lexical / sparse** | BM25: exact words, no model |
| **dense / semantic** | embeddings: meaning, a model turns each text into a vector, cosine compares them |
| **bi-encoder / cross-encoder** | the embedding model encodes question and chunk apart (fast, all chunks); the reranker reads them together (precise, few candidates) |
| **query marker** | text some embedding models want before the question (Qwen3: an instruction), added at the encode call, never stored |

## Step 1: the chunks (`engine/corpus`)

`./mvnw -q -pl corpus -am compile exec:java` (from `engine/`) writes `ai/.corpus/chunks.jsonl`, not committed: a handoff only `python -m rag index` reads. **The database is the RAG's single source of chunks** (the maintainer's decision): every retriever of an evaluation is built from `ChunkRepository.find_all_chunks()`, so a forgotten index can't mix two corpus versions in one run. 130 rules, 13 terms, 48 cards. Format and code map: `engine/corpus/README.md`.

- **Rules**: the rulebook's Markdown kept (its bold marks names, defined terms, emphasis), items and examples included, prefixed with the **titles of the rule's sections, read from its ID**: 5.2.1 → `[5. Game sequence > 5.2 Start of turn (in this order)]`. The ID itself stays out of the text (no question quotes one; the prompt's tags carry it).
- **Terms and cards** name their own subject: no prefix. A card: a stats line, then `CardTextRenderer`'s text.
- **Refs**: a rule's are the numbers its text writes, found by regex; a card's are read from its data (`CardRefs`: keyword → section, trigger and effect → rule, a summoned token → its card), with no-default switches so a new keyword, trigger or effect does not compile unmapped. Kept as written, then a second pass (`SectionRefs`) turns a section number into its rules; a test fails on a ref to nothing.
- `README.md` and `12-open-points.md` make no chunk. An example belongs to a rule only when indented under it.
- `DatasetCitationsTest` (engine) checks that every citation of every committed dataset version has its chunk: otherwise recall could never reach 100%, silently.

## Steps 2 and 3: measuring retrievers (`ai/rag`)

`uv run python -m rag evaluate --description "<why>"` (from `ai/`; every retriever by default, or name some) prints one table per retriever: Recall@10, Recall@20, MRR, the candidate recall and the number of candidates, overall and by category.

Layout, one package per responsibility (the maintainer wants to find anything at a glance): `rag/__main__.py` only parses, calls a service and prints; `indexing/` and `evaluation/` hold one service each, one per command; `repository/` is where the RAG keeps things (`database.py`, `entities.py`, one repository per table: a contract and its `…Postgres` implementation, with `…InMemory` fakes in the tests, the maintainer's suffix convention; and `evaluation_repository.py`, `EvaluationRepositoryFile` today, a bucket later); `retrievers/` holds the `Retriever` contract, `registry.py` (every retriever by name) and one sub-package per family (`random/`, `bm25/` with `bm25_variants.py`, `dense/` with `embedding_models.py`, `chunk_embedding.py`, `chunk_embedder.py`, `fusion/` with `rrf_retriever.py`, `expansion/` with `expansion_retriever.py`); `evaluation/` holds the whole evaluation flow in `evaluation_service.py` (`evaluate_all` builds each retriever, `evaluate` runs it on every question and scores), plus `questions.py`, `retrieval_metrics.py`, `scores.py`, `runs.py` (`Evaluation`, `Run`, `Layers`), `evaluation_json.py` (the document and its name), `code_commit.py`, `report_table.py`. **Each `evaluate` is one evaluation, saved as its own file** (the maintainer's decision): `evals/reports/<version>/<split>/<timestamp>.json`, UTC, never overwritten, **no index file** (one more thing to maintain: the frontend reads the timestamps). It holds the dataset, the time, the code commit (`-dirty` when a tracked file outside `evals/reports/` changed), its description (why it was run, a required `--description`; the maintainer asked for it, 2026-10-17), and its runs, **each named by its layers** (`{"retriever": "bm25", "answer": null}`; the maintainer wants the frontend to switch between runs by layer) and described by them (the retriever's `description`, which the fusion and the expansion compose: "RRF (k = 60) of BM25 (…) and dense retrieval (…)") with its scores and each question's first 20 chunks. **Storage is behind a contract on both sides, so a bucket is one more implementation**: Python's `EvaluationRepository.save_evaluation` (`EvaluationRepositoryFile` today; `evaluation_json.py` gives the document and its name, a repository only stores them), and the game server's `EvaluationDao` (`EvaluationDaoFile` reads the reports folder at each request, so a new evaluation is served without a restart), behind `EvaluationPort` like the games. The server serves each document as written, its format belonging to `evaluation_json.py`: `GET /api/evaluations/{version}/{split}` lists the timestamps, oldest first, and `GET /api/evaluations/{version}/{split}/{timestamp}` returns one (404 if it never ran). **Name for the future**: what the evaluation will share once answers are scored is generic (`Question`, `Scores`, `Report`, `evaluate`; `read_questions` returns the unanswerable questions too, which abstention will need); only what really is retrieval says so (`retrieval_metrics.py`, `RECALL_CUTOFFS`). The maintainer was angry at `RetrievalScores`. Loops are written as explicit `for` loops, not comprehensions that hide them. Never pass two lists that only line up by position (chunks and vectors): make one object per item where it is produced (`EmbeddingModel.embed_chunk(chunk)` returns a `ChunkEmbedding(chunk, vector)`), and no `zip`.

- **Recall@10** is what the prompt holds after the reranker; **Recall@20** the top 20 the refs expansion starts from, and the 20-chunk prompt variant.
- **Candidate recall** is the share of the relevant chunks among every chunk a retriever returns, however many: what the reranker receives, a ceiling for what it can keep. **Candidates** is their number, without which returning all 191 chunks would score 100% (random, dense, the fusion, bm25l and bm25+ do; the other BM25 variants return only the chunks sharing a word). Added for step 6 (the maintainer's decision, 2026-10-17): the expansion returns ~41 candidates, and Recall@20 cannot see past the 20th.
- Unanswerable questions have no relevant chunk and are left out: **61 of the 72 dev questions**. One question moves a score by ~1.6 points and a 7-question category is noise: dev gives a direction, test (596) the published numbers. `--split test` is never used to choose anything.
- The **random** retriever is the floor and proves the measure. Best possible Recall@10 is 100% on v1 and v2 (no question needs more than 10 rules).
- A retriever extends the `Retriever` abstract class: `rank_chunks(question) -> list[str]`, chunk ids best first, each once, and a `description` property written from its settings. Contracts are `ABC`s that classes extend explicitly, as in Java, not implicit `Protocol`s: the maintainer wants to see the contract in the class line. `retrievers/registry.py` names each retriever; a variant is one more entry, in its family's list, that changes **one** setting.

Numbers on v2 dev. Only its four reworded death-trigger questions score differently than on v1: BM25's and dense retrieval's recalls move by 0.2 points at most, the random floor by about 1.

| Retriever | Recall@10 | Recall@20 | MRR | Candidate recall | Candidates |
|---|---:|---:|---:|---:|---:|
| random | 8.9% | 11.9% | 0.093 | 100% | 191 |
| bm25 (Lucene, stemming, stop words kept) | 53.3% | 59.3% | 0.575 | 96.6% | 117.9 |
| dense-qwen3-0.6b | 62.3% | 74.1% | 0.567 | 100% | 191 |
| rrf-bm25-dense (k = 60) | 59.6% | 78.6% | 0.565 | 100% | 191 |
| rrf-bm25-dense-expanded (top 20) | 56.3% | 68.7% | 0.572 | **95.3%** | **41.3** |

BM25 finds factual (100% Recall@20) and simple-rule questions (92%), and misses scenario questions (they name cards, not the rules they need) and deck questions (the decklist floods the ranking; the question never says "30"). Dense retrieval finds deck questions by meaning (92% Recall@20, BM25 0%) and every simple-rule and factual question, but misses multi-step ones (3%, BM25 36%): the two complement each other, which is the case for fusion. The five formulas and the stemming and stop-word variants are within about two questions of each other. bm25s removes English stop words **by default**: the baseline keeps them ("not", "no" carry meaning in rules).

## Step 4: dense retrieval

`scripts/build-index.sh` (from the root) writes the chunks, starts the database and embeds; then `uv run python -m rag evaluate dense-qwen3-0.6b --description "…"`; `scripts/evaluate.sh dense-qwen3-0.6b --description "…"` runs both (how to run and check the whole pipeline: the `shardbound-pipeline-run` skill). Setup for a new machine: the end of `ai/README.md`.

- **Qwen3-Embedding-0.6B** (sentence-transformers, CPU, 1024 dimensions), one entry of `LOCAL_EMBEDDING_MODELS` (`retrievers/dense/embedding_models.py`): our name → Hugging Face id. One class, `LocalEmbeddingModel(name)`, runs any of them, one instance per model. Each entry becomes a `dense-<name>` retriever and is indexed by `python -m rag index`. The model's own configuration adds its query marker (`encode_query`: an instruction before the question; `encode_document`: nothing). It loads only when there is something to embed.
- **Postgres + pgvector** (`db` service of `compose.yaml`, `rag/repository/`), through **SQLAlchemy** (the maintainer refused SQL in strings): two entities, `ChunkRow` (the chunk with its refs, for the whole RAG) and `ChunkEmbeddingRow` (one vector per chunk and model, with the hash of the text it was made from); the tables are created from them, no migrations yet (Alembic when data must survive a schema change). Only changed chunks are embedded again. pgvector ranks by cosine distance in SQL (`<=>`); every chunk has a cosine, so all are ranked.
- `uv` installs the CPU build of torch on every platform (`pyproject.toml` points at PyTorch's CPU index), CI included.
- CI never downloads a model or starts a database: `tests/rag/retrievers/dense/conftest.py` has a fake model (word counts) and `EmbeddingRepositoryInMemory`, with the same contract as `EmbeddingRepositoryPostgres`. The SQL is proven by a real index and evaluate run, not by CI.
- Not done yet: the index does not record the rulebook's commit, which design §5.2 asks for. An evaluation records the code commit, which says which rulebook was indexed only if the index was rebuilt at that commit.

## Step 5: RRF fusion

`RrfRetriever` (`retrievers/fusion/rrf_retriever.py`) asks each retriever for its ranking and scores a chunk Σ 1 / (60 + rank) over the rankings it is in. A chunk that a retriever leaves out (BM25 leaves out the chunks sharing no word with the question) scores nothing from that one; on a tie, the first rankings' order wins. The registry's `rrf-bm25-dense` fuses the baseline `bm25` and `dense-qwen3-0.6b`, each built as it is alone. One fusion only (the maintainer's decision): the BM25 variants are within two questions of each other on dev, so a fusion per variant would be noise; a fusion variant changes one of its own settings (k, or one of its rankings).

On v2 dev, Recall@20, what the reranker would receive without the refs expansion, rises to 78.6% (dense 74.1%), and Recall@10 falls to 59.6% (dense 62.3%). The fusion brings BM25's multi-step finds into the top 20 (24.0%, dense 2.6%) and every deck question (100%: rule 2.1, 24th for BM25 and 22nd for dense, lands 20th). But a chunk only one retriever ranks well sinks under chunks both rank fairly: for "What does it mean to sacrifice a unit?", 8.3 is 2nd for dense and 67th for BM25, so 17th fused, behind 8.16 (7th and 5th). Before the refs expansion, the reranker would reorder this top 20, so Recall@20 is this step's measure. Interaction questions stay at 0%: they cite rules that only their cards' refs reach.

Card names found in the question moved to step 6 (the maintainer, 2026-10-16): a card chunk is not a citation of a scenario question, so adding it cannot raise recall before its refs are followed.

## Step 6: card names and the refs expansion

A retriever ranks by likeness to the question, but an interaction question names cards and cites rules it shares only common words with ("I have Ash Warden… I cast Roaring Pyre then Tempest" needs 11.1.9 and 8.5: BM25 ranks them 49th and 83rd, dense 74th and 191st). The link is a second hop, question → card → rule, which the refs encode.

`ExpansionRetriever(retriever, chunks, top_k=20)` (`retrievers/expansion/expansion_retriever.py`) wraps a retriever of the same chunks. Its candidates, each chunk once: the chunks of the cards the question names, then the wrapped retriever's top 20, then the refs of all of these, one level deep. Each chunk carries its `name` (written by `engine/corpus`: a card's or a term's, null for a rule; the Java `Chunk` record refuses anything else). Card names are matched exactly, as whole words, on the lowercased question, the longest first; matching while ignoring case would let a letter such as "ſ" match a name it cannot then be looked up by. Terms are never matched: "destroy", "die" are in most questions. The names and refs come from the chunks the evaluation read once: the expansion adds no database call per question. The registry's `rrf-bm25-dense-expanded` expands the fusion.

On v2 dev: 95.3% of the relevant chunks among 41.3 candidates (max 65), against 78.6% in the fusion's top 20; interaction questions (7) 0% → 89.8%, multi-step (8) 24.0% → 73.1%, deck, factual and simple-rule stay at 100%. Its Recall@10 and Recall@20 measure the order the candidates are assembled in, named cards first, not a ranking: a deck question names its whole list (15 to 17 cards), which can push rule 2.1 past the 20th place. The reranker sorts them (step 7). The report keeps 20 ids per question, so the comparator cannot show the refs past the 20th yet.

Decided with the maintainer (2026-10-17): exact names, no typo handling (a tolerant layer, edit distance or an LLM, becomes a variant once questions with typos can measure it; plurals such as "two Sprouts" are not matched either); the name is a field of the chunk rather than parsed from its text; the candidate recall and the number of candidates measure what reaches the reranker, which will return every candidate reordered, the prompt taking the first 10; the refs are attached before the reranker (done), and attaching them again after it is measured at step 7, which decides the baseline.

## Settled, and still open

Settled with the maintainer: relevant chunks = citations; the prompt holds **10 chunks** (a chunk is ~40 tokens; 5 would cap 8% of the questions); prompt size 5 and 20 are variants; refs are attached **before** the reranker, so a chunk found only through a ref is judged like the others; bm25s + PyStemmer over a hand-written BM25; the database is the RAG's only source of chunks.

Decided for later (the maintainer, 2026-10-14): when the FastAPI service is built, indexing becomes one of its endpoints. The engine posts its chunks once its batch ends, instead of writing `chunks.jsonl`; the service saves them, answers `202 Accepted` at once and embeds the changed ones **asynchronously** in the background (design §5.2). Not before: the batch would otherwise need a running server.

Open, to raise when the step comes:

- **Step 7**: design §5.2 also attaches refs again *after* the reranker ("top 10, with their refs → prompt", and the Prompt bullet). The maintainer wants both orders measured (2026-10-17): refs before the reranker only, and also after it, with the same retriever and reranker, on the cited rules that reach the prompt and the prompt's size.
- A smaller-k RRF *(proposed)*: k = 60 put the sacrifice rule 8.3, 2nd for dense and 67th for BM25, 17th fused; once the reranker exists, a smaller k is one more variant.
- A "perfect" retriever row (the ceiling) was proposed, not asked for.
- A "whole corpus in the prompt, no retrieval" baseline (~7,400 tokens) is deferred: it is judged on answer metrics only. Not in the design.
- Questions in French: BM25 fails on them, a multilingual embedding model does not. An experiment idea only, not in the design.

## Docs to keep in sync

`docs/design.md` §5.2, §6.2, §6.3 and its changelog; `CLAUDE.md` (status, commands); `ai/README.md`; `engine/corpus/README.md`; this skill's plan table and numbers.
