# Shardbound AI

The Python side of the project: the code that calls or evaluates models (design doc §3.1). It holds one step of the Arbiter's evaluation dataset, the wording and the split (`dataset/`), and the Arbiter's RAG, built step by step (`rag/`). The eval harness comes next, in the same `uv` project.

```bash
# from ai/
uv run pytest                                      # the tests; no model is called
uv run python -m dataset build v2 --base v1        # word and split a version (scripts/build-dataset.sh runs both steps)
uv run python -m rag evaluate random bm25          # score retrievers on the dev questions
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

**The chunks** come from `engine/corpus` (Java), which writes `ai/.corpus/chunks.jsonl`: one per rule, vocabulary term and card, with their refs. Write them before a run, from `engine/`: `./mvnw -q -pl corpus -am compile exec:java`.

**The retrieval metrics** score a retriever, which ranks the chunk ids for a question, best first:

```text
random on v1 dev, 61 answerable questions

| Questions | Count | Recall@10 | Recall@20 | MRR |
|---|---:|---:|---:|---:|
| all | 61 | 2.7% | 9.0% | 0.051 |
| ...
```

- **Recall@k** is the share of a question's relevant chunks found among the first k: 10 is what the prompt holds after the reranker, 20 what the reranker receives. It decides between two retrievers.
- **MRR** is the mean of 1 / the rank of the first relevant chunk: it says whether the good chunks come first.
- A question's relevant chunks are its `citations`. An unanswerable question has none and is left out. Every question weighs the same.
- The random retriever is the floor. With 61 questions, one question moves a score by about 1.6 points, and a category of 7 questions is noise: dev says which way to go, test (596 questions) gives the published numbers.

| Module | Role |
|---|---|
| `rag/chunks.py` | Reads the chunks `engine/corpus` writes |
| `rag/retrievers/` | The `Retriever` interface (`rank_chunks`), then one module per retriever: `random_retriever.py`, `bm25_retriever.py` |
| `rag/__main__.py` | The retrievers by name: `random`, then `bm25` and its variants, each changing one setting (`bm25-no-stemming`, `bm25-stop-words-removed`, `bm25-robertson`, `bm25-atire`, `bm25l`, `bm25+`) |
| `rag/evaluation/questions.py` | The answerable questions of a split, with their relevant chunks |
| `rag/evaluation/metrics.py` | Recall@k and the reciprocal rank of one question |
| `rag/evaluation/report.py` | The means over a split, overall and by category, as a Markdown table |
