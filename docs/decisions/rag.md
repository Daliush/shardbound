# RAG decisions

Why the RAG Arbiter is built the way it is, step by step, and what was tried or rejected. The design (`docs/design.md` §5.2, §6.2, §6.3) says what the pipeline is; this file keeps the reasons. Each decision was taken with the maintainer. A new one goes at the end of its step.

## How it is measured

- **A question's relevant chunks are its citations.** The answer key already says what a correct answer must cite, so nothing is labelled by hand.
- **Recall@10 decides, MRR explains** (2026-10-12). A chunk that is not retrieved cannot be used, and the prompt holds 10.
- **The prompt holds 10 chunks** (2026-10-12). A chunk is about 40 tokens and no question needs more than 10 rules; 5 would cap 8% of the questions. 5 and 20 are variants.
- **A recall curve, not a composite score** (2026-10-17). To show that the refs expansion does better with fewer chunks, runs are compared at the same number of chunks. A score weighing recall against size was rejected: arbitrary weights could change the winner (design §6.3).
- **No candidate recall** (2026-10-17). It always equalled the curve's last value, and was 100% for every retriever that returns the whole corpus.
- **One file per evaluation, and no index file.** A file is never overwritten; an index would be one more thing to keep in sync, and the frontend reads the timestamps. Each run is named by its layers, so that the frontend switches between runs by layer.
- **Every evaluation says why it was run** (2026-10-17), so that the benchmark explorer can show it.
- **CI fails a pull request whose Recall@10 drops by more than 2 points** *(proposed, 2026-10-19)*. One dev question moves a score by about 1.6 points, so a drop of 2 is more than one question; a change meant to lower recall records a new evaluation with it, and the drop shows in its diff.
- **A new report format replaces the old reports** (2026-10-18). The first report of the reranker's format replaced every older one rather than converting them.
- **Binary nDCG@10 and @20 explain too** (2026-10-22), at the same cutoffs as Recall. Agreed on 2026-10-17 for after the reranker, it is computed on every run, like MRR, so it also shows what the reranker adds: on v2 dev, the binary nDCG@10 of the fusion with the refs is 0.550 without the reranker and 0.623 with it. Every cited chunk gains 1: the context rules are full citations, so no chunk weighs more than another. It shows what Recall@10 hides: a cited chunk at rank 10 counts as much as one at rank 1 for Recall@10, but 0.29 for nDCG. A graded nDCG would sit next to it under its own name.
- **Each metric is a class that describes itself** (2026-10-22). It scores one question from the ranks of its cited chunks, and says what it does, how and what it represents; the evaluation loops over the list of metrics, and every evaluation file keeps the descriptions, which the benchmark explorer shows. One interface only: the values that are not a metric's mean (the count, the candidates, the recall curve, the latency) are computed by `score_group` and only described.

## Step 1: the chunks

- **Java writes the chunks.** The rulebook parser and the card text (`CardTextRenderer`) are in the engine.
- **A rule's prefix is the titles of its sections, without its ID; cards and terms get none** (2026-10-12). No question quotes a rule number, and the prompt's tags carry it.
- **A section number stands for all its rules** (2026-10-12): the "(7)" of rule 5.3.1 brings the combat rules.
- **A chunk carries its name** (2026-10-17), written by Java (a card's or a term's), not parsed from its text.
- **The database is the RAG's only source of chunks** (2026-10-14). Every retriever of an evaluation is built from the indexed chunks, so a forgotten index cannot mix two versions of the corpus in one run. `ai/.corpus/chunks.jsonl` is only what the index reads.

## Step 3: BM25

- **bm25s and PyStemmer, rather than a hand-written BM25** (2026-10-12).
- **The baseline keeps stop words.** bm25s removes English stop words by default, but "not" and "no" carry meaning in rules.
- What it showed on dev: BM25 finds the factual questions (100% Recall@20) and the simple-rule ones (92%). It misses the scenario questions, which name cards rather than the rules they need, and the deck questions, whose decklist floods the ranking and which never say "30". Its five formulas and its stemming and stop-word variants are within about two questions of each other.

## Step 4: dense retrieval

- **Qwen3-Embedding-0.6B on CPU is the baseline** (2026-10-14), one entry of a registry of local models: another model is one more entry.
- **SQLAlchemy, no SQL in strings.**
- **No migrations yet.** The tables are created from the entities, so a schema change means an empty database and every chunk embedded again. Alembic comes when data must survive a change.
- What it showed: dense retrieval finds the deck questions by meaning (92% Recall@20, BM25 0%) and every simple-rule and factual question, but misses the multi-step ones (3%, BM25 36%). The two complement each other, which is the case for fusion.

## Step 5: RRF fusion

- **One fusion only** (2026-10-16). The BM25 variants are within two questions of each other on dev, so a fusion per variant would measure noise. A fusion variant changes one of its own settings.
- **The card names moved to step 6** (2026-10-16). A card chunk is not a citation of a scenario question, so adding it cannot raise recall before its refs are followed.
- What it showed: Recall@20 78.6% (dense alone 74.1%), Recall@10 59.6% (dense 62.3%). A chunk that only one retriever ranks well sinks under chunks both rank fairly: rule 8.3, 2nd for dense retrieval and 67th for BM25, comes 17th.

## Step 6: card names and the refs expansion

- **Card names are matched exactly, as whole words, on the lowercased question, with no typo handling** (2026-10-17). A tolerant layer (edit distance, an LLM) becomes a variant once questions with typos can measure it. Plurals ("two Sprouts") are not matched either. Matching while ignoring case was dropped after a review: a letter such as "ſ" matches a name that it cannot then be looked up by.
- **Vocabulary terms are never matched**: "destroy" and "die" are in most questions.
- **The refs are attached before the reranker** (2026-10-12), so a chunk found only through a ref is judged like the others.
- **And not again after it** (2026-10-23). The reranker has already ranked them among the candidates, and the prompt keeps its 10 chunks. The variant asked on 2026-10-17 was dropped by the maintainer without being measured.
- What it showed: 95.3% of the relevant chunks among 41.3 candidates on average, against 78.6% in the fused top 20; interaction questions from 0% to 89.8%, multi-step ones from 24% to 73.1%.

## Step 7: the reranker

- **The reranker is a step of its own, not a retriever** (2026-10-18). Several rerankers will be tried, and the explorer must build a pipeline with or without each one.
- **One class per reranker, which loads its own model** (2026-10-18). A model class beside the reranker class made two classes that rank.
- **The pipeline is steps, and the evaluation is the tree of every pipeline they make** (2026-10-18), each node computed once and knowing its parent. Rejected first: a retriever computed again for each reranker, a cache inside the retriever, and a ranking handed back to the retriever to cut.
- **A reranker reads the candidates, never the corpus** (2026-10-18, as design §5.2 says). A retriever hands on its top 20, the expansion its whole list.
- **MiniLM-L6 comes first** (2026-10-18): 22M parameters, the classic small baseline. Qwen3-Reranker-0.6B and Jev *noul* come later.
- **Qwen3-Reranker-0.6B is the second reranker** (2026-10-23): a cross-encoder built on a 0.6B LLM, whose score is the logit of "yes" minus the logit of "no". It loads from its official repository with sentence-transformers, like MiniLM, so both extend one `CrossEncoderReranker`; its revision is pinned, since the repository was changed after release to load that way. It is told the task by one generic instruction, "Given a question about a card game's rules, retrieve the rules and cards needed to answer it.", in place of its default one about web search.
- **The rerankers load their models in float32** (2026-10-25). Qwen3-Reranker's weights are stored in bfloat16, which keeps about 3 significant digits: on the same CPU, the batch size alone moved its scores by about 0.07, enough to swap close candidates. In float32, the CPU and a GPU ranked 6 dev questions identically (scores within 0.0001), whatever the batch size. It costs speed on the CPU: Qwen3-Reranker takes about 50 s a question instead of 17 s.
- **The embedding model loads in float32 too** (2026-10-25). Qwen3-Embedding's weights are stored in bfloat16 too: the first evaluation on the GPU embedded the questions with AMD's build of torch, the chunks having been embedded with the locked one, and cosines moved by up to 0.006. Recall@10 did not move, but the dense retriever's top 20 stayed the same for 18 dev questions out of 61 only. In float32, two builds give cosines within 0.000001; the index was embedded again. The vectors are kept under the model's name and format (`qwen3-0.6b/float32`), so that an index embedded in another format is never reused: a change of format embeds every chunk again by itself. On the CPU of AMD's build, float32 is slow (2.7 s a question), so the embedding model runs on `RAG_DEVICE` too.
- **A GPU through `RAG_DEVICE`, the CPU by default** (2026-10-25). On the maintainer's RX 6800 XT, with AMD's ROCm build of torch, Qwen3-Reranker takes 1.3 s a question in float32 (about 50 s on the CPU) and MiniLM-L6 0.065 s (1.15 s), with the CPU's rankings. CI and other machines stay on the CPU. The GPU build of torch lives in a second environment, `ai/.venv-gpu`, beside the locked one, so that a plain `uv run` (the test hook's) never replaces it under a running evaluation; a uv extra for it would need the lock re-resolved and tried first. Each evaluation records its device and torch build, since latency depends on them and the commit alone does not say which torch ran.
- What it showed (2026-10-25, every model in float32, on the GPU): the whole pipeline puts 71.1% of the cited chunks in the prompt's 10 with MiniLM-L6 and 70.7% with Qwen3-Reranker, a tie, since a question is worth 1.6 points: MiniLM-L6 stays the baseline. Qwen3-Reranker puts 86.7% in its 20 (MiniLM-L6 81.3%) and ranks above MiniLM-L6 at 10 after 16 of the 20 candidate lists, mostly the weaker ones (dense retrieval, BM25), but finds the first cited chunk later (MRR 0.620 against 0.661) and takes ten times as long (1.15 s a question on the GPU, against 0.11 s). It brings rule 8.2 (Destroy) into the 10 when a question never says "destroy" (Tempest, Shardbreak Pulse), and sinks rule 9.11, the order in which an action and its triggers resolve, from 1st to 11th. The 72.7% of 2026-10-22 was one question luckier: with the index in bfloat16, rule 2.1 came 20th in the dense ranking of a deck question; in float32 it comes 21st, out of the top 20 the reranker reads.

## Later

- **Indexing moves behind the FastAPI service, and becomes asynchronous** (2026-10-14). Once the service exists, the engine posts its chunks, and the service answers `202 Accepted` and embeds them in the background. Not before: the engine's batch would need a running server.

## Open

- One Qwen3 model loaded once for every dense-based retriever.
- A smaller k for RRF *(proposed)*: k = 60 put rule 8.3 17th.
- A "perfect" retriever, the ceiling *(proposed, not asked for)*.
- The whole corpus in the prompt, with no retrieval (about 7,400 tokens), judged on answer metrics only (deferred, 2026-10-12).
- Questions in French: BM25 fails on them, a multilingual embedding model does not. An idea only.
