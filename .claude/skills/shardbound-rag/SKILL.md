---
name: shardbound-rag
description: How Shardbound's RAG Arbiter is built and how to work on it with the maintainer — the step-by-step plan and where it stands, the chunks engine/corpus writes (rules, vocabulary terms, cards, refs), the retrieval metrics of ai/rag (relevant chunks = citations, Recall@10 / Recall@20, MRR, the random floor, dev noise), the retriever interface and its registry of variants (random, BM25 and its settings, dense retrieval with pgvector), the decisions already settled and the points still open. Use this whenever you touch engine/corpus or ai/rag, add or tune a retriever, an embedding model, the reranker or the prompt, read or explain retrieval numbers, or explain a RAG concept to the maintainer — even for a quick question about a chunk or a metric.
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
question ─┬→ card names found     → their card chunks, added directly       (step 5)
          ├→ BM25                 → list A ┐                                (step 3)
          └→ embedding → cosine   → list B ┘→ RRF (k = 60) → top ~20        (steps 4, 5)
top ~20 → + refs (one level) → cross-encoder reranker → top 10 → prompt     (steps 6, 7, 8)
```

| Step | What | Where | State |
|---|---|---|---|
| 1 | Chunks with their refs | `engine/corpus` (Java) | done |
| 2 | Retrieval metrics, random floor | `ai/rag/evaluation` | done |
| 3 | BM25 and its variants | `ai/rag/retrievers/bm25_retriever.py` | done |
| 4 | Dense retrieval: embeddings in pgvector, cosine | `ai/rag` + Postgres in `compose.yaml` | next |
| 5 | RRF fusion, card names found in the question | `ai/rag` | |
| 6 | Refs expansion | `ai/rag` | |
| 7 | Cross-encoder reranker | `ai/rag` | |
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

`./mvnw -q -pl corpus -am compile exec:java` (from `engine/`) writes `ai/.corpus/chunks.jsonl`, not committed. 130 rules, 13 terms, 48 cards. Format and code map: `engine/corpus/README.md`.

- **Rules**: the rulebook's Markdown kept (its bold marks names, defined terms, emphasis), items and examples included, prefixed with the **titles of the rule's sections, read from its ID**: 5.2.1 → `[5. Game sequence > 5.2 Start of turn (in this order)]`. The ID itself stays out of the text (no question quotes one; the prompt's tags carry it).
- **Terms and cards** name their own subject: no prefix. A card: a stats line, then `CardTextRenderer`'s text.
- **Refs**: a rule's are the numbers its text writes, found by regex; a card's are read from its data (`CardRefs`: keyword → section, trigger and effect → rule, a summoned token → its card), with no-default switches so a new keyword, trigger or effect does not compile unmapped. Kept as written, then a second pass (`SectionRefs`) turns a section number into its rules; a test fails on a ref to nothing.
- `README.md` and `12-open-points.md` make no chunk. An example belongs to a rule only when indented under it.
- `DatasetCitationsTest` (engine) checks that every citation of every committed dataset version has its chunk: otherwise recall could never reach 100%, silently.

## Steps 2 and 3: measuring retrievers (`ai/rag`)

`uv run python -m rag evaluate random bm25 …` (from `ai/`) prints one table per retriever: Recall@10, Recall@20, MRR, overall and by category.

- **Recall@10** is what the prompt holds after the reranker; **Recall@20** what the reranker receives.
- Unanswerable questions have no relevant chunk and are left out: **61 of the 72 dev questions**. One question moves a score by ~1.6 points and a 7-question category is noise: dev gives a direction, test (596) the published numbers. `--split test` is never used to choose anything.
- The **random** retriever is the floor and proves the measure. Best possible Recall@10 is 100% on v1 (no question needs more than 10 rules).
- A retriever implements one method, `rank_chunks(question) -> list[str]` (chunk ids, best first). The registry in `rag/__main__.py` names each retriever; a variant is one more entry that changes **one** setting.

Numbers on v1 dev:

| Retriever | Recall@10 | Recall@20 | MRR |
|---|---:|---:|---:|
| random | 2.7% | 9.0% | 0.051 |
| bm25 (Lucene, stemming, stop words kept) | 53.5% | 59.3% | 0.583 |

BM25 finds factual (100% Recall@20) and simple-rule questions (92%), and misses scenario questions (they name cards, not the rules they need) and deck questions (the decklist floods the ranking; the question never says "30"). The five formulas and the stemming and stop-word variants are within about two questions of each other. bm25s removes English stop words **by default**: the baseline keeps them ("not", "no" carry meaning in rules).

## Step 4: dense retrieval (next)

- One embedding model for now, **Qwen3-Embedding-0.6B** (sentence-transformers, CPU), behind a registry of local embedding models so another is one entry; each model becomes a `dense-<model>` retriever, like the BM25 variants.
- Storage in **Postgres + pgvector** (a `db` service in `compose.yaml`): the chunks with their refs, and their vectors per model, re-embedded only when a chunk's `hash` changes. pgvector computes the cosine in SQL (`<=>`). Every chunk has a cosine, so all are ranked.
- CI never downloads a model or starts a database: tests use a fake model and a fake store. Running it on another machine must work from a written setup (uv, JDK 21, Docker) and one script.

## Settled, and still open

Settled with the maintainer: relevant chunks = citations; the prompt holds **10 chunks** (a chunk is ~40 tokens; 5 would cap 8% of the questions); prompt size 5 and 20 are variants; refs are attached **before** the reranker, so a chunk found only through a ref is judged like the others; bm25s + PyStemmer over a hand-written BM25.

Open, to raise when the step comes:

- **Step 6**: design §5.2 also attaches refs again *after* the reranker ("top 10, with their refs → prompt", and the Prompt bullet). That adds unjudged chunks; decide with him.
- A "perfect" retriever row (the ceiling) was proposed, not asked for.
- A "whole corpus in the prompt, no retrieval" baseline (~7,400 tokens) is deferred: it is judged on answer metrics only. Not in the design.
- Questions in French: BM25 fails on them, a multilingual embedding model does not. An experiment idea only, not in the design.

## Docs to keep in sync

`docs/design.md` §5.2, §6.2, §6.3 and its changelog; `CLAUDE.md` (status, commands); `ai/README.md`; `engine/corpus/README.md`; this skill's plan table and numbers.
