# Shardbound AI

The Python side of the project: the code that calls or evaluates models (design doc §3.1). It holds one step of the Arbiter's evaluation dataset, the wording and the split (`dataset/`), and the Arbiter's RAG, built step by step (`rag/`). The eval harness comes next, in the same `uv` project.

```bash
# from ai/
uv run pytest                                      # the tests; no model is called
uv run python -m dataset build v2 --base v1        # word and split a version (scripts/build-dataset.sh runs both steps)
uv run python -m rag index                         # write the chunks and their vectors into the database
uv run python -m rag evaluate --description "…"    # score every retriever on the dev questions (or name some)
uv run python -m rag compare <recorded> <new>      # two evaluations run by run; --max-drop fails on a lower recall
```

## What `dataset/` does

Every question and every answer key comes from `engine/dataset` (Java): the scenarios, the factual and deck questions it computes, and the simple-rule and unanswerable questions it keeps, written once. This module only does what needs a model, and the split:

1. **Wording.** Each question is rewritten the way a player would ask it, then checked twice: by code (every card name, keyword and number kept, no card or rule number added), then by a second model call that compares it with the template (same situation, same question). A rewrite that fails keeps its template, and the manifest lists it with its reason. A rewrite can pass both checks and still be wrong: `kept-templates.json` in the version's folder maps such a question to the reason a review gave, and it keeps its template. A deck question is reworded without its decklist, which is appended afterwards, so the model never sees the deck.
2. **Split.** Each question has a group, and a group sits on one side only: an interaction, a card, a rule, a deck mutation, an invented name or an open point. About 10% of each category's groups go to dev, drawn with a fixed seed.
3. **Output.** The two final datasets, `dataset-dev.jsonl` (to tune the RAG) and `dataset-test.jsonl` (for the published numbers), and `manifest.json`. The version's `hand-check.jsonl`, the sample checked by hand, is refreshed from the new datasets, its reviews kept.

**Nothing is worded or drawn twice.** With `--base <previous version>`, a question whose template has not changed keeps the previous version's wording, and a group keeps its side: only new or changed questions go to the model, and only new groups are drawn. Scores then stay comparable between versions, since the same question reads the same. Each new version becomes the base of the next.

**The model never writes an answer.** It only rewords a question.

## The model

Luna 6 (`gpt-6-luna`), a family no evaluated configuration uses (design doc §3.3), called through an OpenAI-compatible chat-completions API:

| Variable | Value |
|---|---|
| `LUNA_BASE_URL` | `https://api.openai.com/v1`, the API root: the client adds `/chat/completions` |
| `LUNA_MODEL` | `gpt-6-luna` |
| `LUNA_API_KEY` | the key |
| `LUNA_REASONING_EFFORT` | optional, sent as `reasoning_effort`; v1 used `low` |
| `LUNA_TEMPERATURE` | optional; 1 by default, the only temperature `gpt-6-luna` takes |

The run prints the model and the settings it sends (never the key) before its first call, and the manifest records them. Every response is cached on disk by model, settings and prompt (`ai/.cache/llm/`, not committed): a rerun calls nothing. Since `gpt-6-luna` cannot run at temperature 0, reproducibility rests on the committed files and `--base`, not on the model.

`--fake` uses a fake model instead, for the tests and for a dry run without a key, into `preview`.

## One line of a final dataset

```json
{"id":"anchor+destroy/opponents/protected/root.root-sentinel/neutral.crystal-rupture","category":"interaction","group":"anchor+destroy","split":"test","question":"My opponent played Root Sentinel last turn. What happens to it if I cast Crystal Rupture on it?","template_question":"My opponent played Root Sentinel on their last turn. If I cast Crystal Rupture on it, what happens to it?","wording":"paraphrase","answerable":true,"source":"engine","key_facts":["Root Sentinel is anchored: it is not destroyed."],"citations":["11.3.2","11.3.3","11.3.1"],"rules":["11.3.2","11.3.3"],"context_rules":["11.3.1"],"cards":["neutral.crystal-rupture","root.root-sentinel"],"trace":["CardPlayed[6.3]","AnchorPrevented[11.3.2, 11.3.3]","SpellResolved[6.3, 3.5]"]}
```

- The Arbiter sees `question` only. The grading reads `key_facts`, `citations` (what a correct answer cites: the rules of a scenario or a deck question, the card of a factual question, the rule of a simple-rule question) and `answerable`. `category`, `group` and `split` serve the reports and the tuning.
- `wording`: `paraphrase`, `template` (the rewrite failed its check), or `written` (a simple-rule question Luna wrote for v1, kept as it was).

## Code

| Module | Role |
|---|---|
| `dataset/build.py` | Reads the engine's five files and the base version, words what is new, splits, writes the two datasets and the manifest |
| `dataset/wording.py`, `dataset/prompts.py` | The rewording and its two checks; the prompts, hashed into the manifest |
| `dataset/content.py` | The card names and keywords the code check protects |
| `dataset/split.py` | The split by group, kept from the base version |
| `dataset/llm.py` | The model behind one interface: the OpenAI-compatible client, the disk cache, the fake |

`tests/dataset/test_versions.py` checks every committed version (each file holds only its side, no group on both sides, counts match the manifest, abstentions exactly for unanswerable questions), so CI guards the data as well as the code.

## What `rag/` does

The Arbiter's RAG (design doc §5.2), one brick at a time, each measured on the dev questions before the next.

**The chunks** come from `engine/corpus` (Java), which writes `ai/.corpus/chunks.jsonl`: one per rule, vocabulary term and card, with their refs. The file is only a handoff: `python -m rag index` reads it into the database, and **the database is the RAG's only source of chunks**. Every retriever of an evaluation is built from the indexed chunks, so a run can never mix two versions of the corpus, even if the chunks were regenerated without indexing.

**The index** lives in Postgres with pgvector (the `db` service of `compose.yaml`): a `chunks` table, and a `chunk_embeddings` table with one vector per chunk and embedding model. `python -m rag index` embeds only the chunks whose text changed since a model last did, so a run where nothing changed loads no model.

The tables are created from the entities when missing, never altered: there are no migrations, since everything in them is rebuilt from the repository. After a change to an entity, drop the database (`docker compose down -v`, from the root) and build the index again: every chunk is embedded again, about a minute on CPU.

**The retrieval metrics** score a retriever, which ranks the chunk ids for a question, best first:

```text
retriever random, answer None on v2 dev, 61 answerable questions

| Questions | Count | Recall@10 | Recall@20 | MRR | Candidates |
|---|---:|---:|---:|---:|---:|
| all | 61 | 8.9% | 11.9% | 0.093 | 191.0 |
| ...
```

- **Recall@k** is the share of a question's relevant chunks found among the first k: 10 is what the prompt holds after the reranker, 20 the prompt of the 20-chunk variant. It decides between two retrievers.
- **MRR** is the mean of 1 / the rank of the first relevant chunk: it says whether the good chunks come first.
- **The recall curve** (`mean_recall_curve`, in the evaluation's file, not the table) is Recall@k for every k from 1 to the longest list: it compares retrievers at the same number of chunks, such as the refs expansion's 41 candidates against the fusion's top 41. Each question also keeps the rank of each of its relevant chunks (`relevant_ranks`), from which Recall@k and MRR are read.
- **Candidates** is the mean number of chunks a run ranks. Without a reranker, the retriever's whole list: random, dense-qwen3-0.6b, rrf-bm25-dense, bm25l and bm25+ return all 191; the other BM25 variants return only the chunks sharing a word with the question; the expansion 41 on average. After a reranker, the candidates it reordered: 20, or the expansion's whole list. The share of the relevant chunks among all of them is the last value of the recall curve: after the expansion, the most a reranker could keep.
- A question's relevant chunks are its `citations`. An unanswerable question has none and is left out. Every question weighs the same.
- The random retriever is the floor. With 61 questions, one question moves a score by about 1.6 points, and a category of 7 questions is noise: dev says which way to go, test (596 questions) gives the published numbers.

`rag/` has one package per responsibility; the command line (`rag/__main__.py`) only reads its arguments, calls a service and prints.

```
rag/
├── __main__.py                 the command line: index, evaluate, compare; it calls a service and prints
├── chunks.py                   the chunks engine/corpus writes
├── comparison/                 two saved evaluations, run by run: comparison_service.py, comparison_table.py
├── indexing/
│   └── indexing_service.py     the index: the chunks into the database, then each model's vectors of the changed ones
├── repository/                 the database, Postgres with pgvector, through SQLAlchemy
│   ├── database.py             the connection; the tables created from the entities
│   ├── entities.py             ChunkRow, ChunkEmbeddingRow
│   ├── chunk_repository.py     ChunkRepository, ChunkRepositoryPostgres
│   ├── embedding_repository.py EmbeddingRepository, EmbeddingRepositoryPostgres: the vectors, ranked by cosine
│   └── evaluation_repository.py EvaluationRepository, EvaluationRepositoryFile: one file per evaluation
├── retrievers/
│   ├── __init__.py             Retriever: rank_chunks(question) → chunk ids, best first; CANDIDATE_COUNT
│   ├── registry.py             every retriever by name
│   ├── random/                 the floor
│   ├── bm25/                   bm25_retriever.py, bm25_variants.py (the list of BM25 variants)
│   ├── dense/                  embedding_models.py (the list of models), chunk_embedding.py (a chunk and its vector),
│   │                           chunk_embedder.py (embed the changed chunks), dense_retriever.py (rank by cosine)
│   └── fusion/                 rrf_retriever.py (several rankings merged by rank)
├── expanders/
│   ├── __init__.py             Expander: expand_chunks(question, chunk ids) → them and the chunks it adds
│   ├── registry.py             every expander by name
│   └── refs/                   refs_expander.py (the named cards and the refs)
├── technique.py                Step (retriever, expand, rerank) and Technique, what the three have in common
├── rerankers/
│   ├── __init__.py             Reranker: rerank_chunks(question, candidate ids) → every candidate, reordered
│   ├── registry.py             every reranker by name
│   └── minilm/                 minilm_reranker.py (MiniLM-L6, a small cross-encoder)
└── evaluation/
    ├── evaluation_service.py   the flow: evaluate_all builds the techniques, evaluate_combinations each retriever's
    │                           node, continue_with the tree under it, apply a node, score its run
    ├── stages.py               Stage, a node of the tree: a technique, its parent, what it handed on per question
    ├── questions.py            Question: every question of a split, its relevant chunks, answerable or not
    ├── retrieval_metrics.py    Recall@k and the reciprocal rank of one question (answer_metrics.py will join it)
    ├── scores.py               QuestionResult, Scores, Report
    ├── runs.py                 Evaluation, Run and its Layers: {"retriever": "bm25", "expand": null, "rerank": null, …}
    ├── evaluation_json.py      an evaluation as the JSON the benchmark explorer reads, and its name
    ├── code_commit.py          the commit evaluated, "-dirty" with uncommitted changes
    └── report_table.py         the Markdown table
```

- A retriever extends `Retriever`. Adding a BM25 variant is one line of `bm25_variants.py`, a dense model one line of `embedding_models.py`; the registry names them `bm25-…` and `dense-<model>`. A fusion is one registry entry naming the retrievers it fuses, built as they are alone: `rrf-bm25-dense`.
- **The pipeline is three steps** (`technique.py`): retriever, expand, rerank. Each technique extends `Technique`, through `Retriever`, `Expander` or `Reranker`, and gets its name from its registry: an expander (`refs`, `RefsExpander`) adds to what it is handed the cards the question names and the refs of all of them; a reranker (`minilm-l6`, `MiniLMReranker`, which loads its own model) returns every candidate it is handed, reordered, the prompt keeping the first 10. A retriever hands the next step its first 20 chunks (`CANDIDATE_COUNT`), every other step its whole list.
- **`evaluate` builds the tree of every pipeline** (`stages.py`): each retriever is a node, and under each node, each technique of the next step makes a child, the step skipped makes none. A `Stage` holds its technique, its parent and what it handed on for each question, so going up the parents gives the run's name and what each step did. Every node is computed once and is a run: 4 per retriever with `refs` and `minilm-l6`, so the benchmark explorer can build any pipeline. A run's list is what its last step handed on: the retriever's whole ranking, the ~41 candidates of the expansion, or the 20 or ~41 a reranker reordered. Each question also keeps how long its pipeline took (`seconds`), and each score block the latency's p50 and p95.
- The database is the RAG's, not only dense retrieval's: `chunks` (a chunk, its name and its refs, which the refs expansion reads) and `chunk_embeddings` (one vector per chunk and model). Each table has a repository contract and its Postgres implementation; the tests use in-memory ones.
- **Each `evaluate` is one evaluation**, a list of runs; each run is named by its layers: `{"retriever": "bm25", "expand": "refs", "rerank": null, "answer": null}`, one technique per step, null for a step it skipped; `answer` will name the generator. The evaluation is saved as a new file, `evals/reports/<version>/<split>/<timestamp>.json` (UTC, e.g. `v1/dev/2026-10-15T10-42-07Z.json`), never overwritten: the dataset, the time, the code commit (`-dirty` when a tracked file outside `evals/reports/` had uncommitted changes), its description (why it was run: `--description` is required and cannot be blank), then per run its description (what each of its techniques does, which each writes from its settings), its scores and recall curve overall and by category, and each question's first 20 chunks, the rank of each of its relevant chunks and its metrics, so the benchmark explorer can switch between runs and compare questions. There is no index: the game server lists a split's timestamps and serves each document as written (`GET /api/evaluations/{version}/{split}` and `…/{timestamp}`).
- `EvaluationRepository` saves an evaluation; `EvaluationRepositoryFile` writes it in the repository. A bucket will be one more implementation: `evaluation_json.py` gives the document and its name, a repository only stores them.

## Running the RAG on a new machine

Needs **JDK 21** (the engine writes the chunks), **uv** (Python and its packages), and **Docker** (the database). No GPU: the embedding models run on CPU.

```bash
# from the repository root: chunks, database and vectors, in one go
scripts/build-index.sh
# then, from ai/
uv run python -m rag evaluate random bm25 dense-qwen3-0.6b --description "A first look"
# or both, from the root: the index, then the evaluation with these arguments
scripts/evaluate.sh random bm25 dense-qwen3-0.6b --description "A first look"
```

- `uv` installs the exact versions of `uv.lock`, with the **CPU build of torch** on every platform (`pyproject.toml` points uv at PyTorch's CPU index), about 200 MB instead of 2 GB.
- The first index run downloads each embedding model once from Hugging Face (Qwen3-Embedding-0.6B: about 1.2 GB) into its cache. On Windows, a warning about symlinks only means the cache copies files instead of linking them.
- The database is local: `docker compose up -d --wait db`, user and password `shardbound`, port 5432. `RAG_DATABASE_URL` points elsewhere, as a SQLAlchemy URL (`postgresql+psycopg://user:password@host:port/database`).
- The tests need none of this: they use a fake model and an in-memory store, and call no model.
