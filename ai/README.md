# Shardbound AI

The Python side of the project: the code that calls or evaluates models (design doc §3.1). It holds the Arbiter's evaluation dataset pipeline (`dataset/`); the RAG (`rag/`) and the eval harness come next, in the same `uv` project.

```bash
# from ai/
uv run pytest                                  # the tests; no model is called
uv run python -m dataset build v1 --fake       # a dry run with a fake model, no key needed
uv run python -m dataset build v1              # the real run, with Luna 6
```

## The evaluation dataset

A dataset version lives in `evals/datasets/<version>/`, committed. It is built in two steps:

1. **The engine** (`engine/dataset`, Java, no model) writes the questions it can answer itself: `scenarios.jsonl` (interaction and multi-step), `cards.jsonl` (factual) and `decks.jsonl` (deck building). From `engine/`: `./mvnw -q -pl dataset -am compile exec:java -Dexec.args=v1`.
2. **This pipeline** adds the other categories, words every question, splits by group and writes `dataset.jsonl`, `hand-check.jsonl` and `manifest.json` next to them.

**Answer keys never come from a model.** They come from the engine, the card data or the rulebook; a model only writes or rewords a question (design doc §6.1).

### What it adds

| Category | How | Answer key |
|---|---|---|
| Unanswerable | No model: invented keywords, cards and mechanics, checked absent from the catalog and the rulebook (`source: "invented"`), and the open points `docs/rules/12-open-points.md` still lists (`source: "open-point"`, wrong once the point is settled) | An abstention: `answerable` is false, no key fact |
| Simple rule | For each rule of sections 1 to 11, parsed from `docs/rules/`, the model writes a question a player would ask, and a second call checks that the rule answers it | The rule's own sentences, the rule as citation |

### Wording

Each template question (scenarios, factual, deck building, unanswerable) is rewritten the way a player would ask it, then checked twice: by code (every card name, keyword and number kept, no card or rule number added), then by a second model call that compares it with the template (same situation, same question, no new card). A rewrite that fails keeps its template; a simple-rule question that fails is dropped, since it has no template. Both are listed in the manifest's `failed_checks`. A deck question's decklist is never sent to the model: only the sentence before it is reworded.

The model is Luna 6, a family no evaluated configuration uses (design doc §3.3), called through an OpenAI-compatible chat-completions API at temperature 0. Set `LUNA_BASE_URL` (the API root, such as `https://api.openai.com/v1`), `LUNA_MODEL` (`gpt-6-luna`) and `LUNA_API_KEY`. `LUNA_REASONING_EFFORT` is optional and sent as `reasoning_effort` when set: `none` turns reasoning off, which rewording a question does not need, and which a reasoning model may require before it accepts temperature 0. The manifest records it with the model. Every response is cached on disk by model and prompt (`ai/.cache/llm/`, not committed), so a rerun costs nothing and gives the same dataset.

### Split

Every question has a group, and a group sits on one side only:

| Category | Group |
|---|---|
| Interaction, multi-step | its interaction |
| Factual | its card |
| Simple rule | its rule |
| Deck building | its mutation kind |
| Unanswerable | its invented name or open point |

About 10% of each category's groups go to dev (at least one when a category has two), drawn with a fixed seed; the rest is test. The split is written into each entry and never recomputed; train comes in phase 4. A built version is frozen: building it again needs `--overwrite`.

### The files

One line of `dataset.jsonl`:

```json
{"id":"anchor+destroy/opponents/protected/root.root-sentinel/neutral.crystal-rupture","category":"interaction","group":"anchor+destroy","split":"test","question":"…the player's wording…","template_question":"My opponent played Root Sentinel on their last turn. If I cast Crystal Rupture on it, what happens to it?","wording":"paraphrase","answerable":true,"source":"engine","key_facts":["Root Sentinel is anchored: it is not destroyed."],"citations":["11.3.2","11.3.3","11.3.1"],"rules":["11.3.2","11.3.3"],"context_rules":["11.3.1"],"cards":["neutral.crystal-rupture","root.root-sentinel"],"trace":["CardPlayed[6.3]","AnchorPrevented[11.3.2, 11.3.3]","SpellResolved[6.3, 3.5]"]}
```

- `wording`: `paraphrase`, `template` (the rewrite failed its check) or `written` (a simple-rule question, written by the model).
- `citations`: the essential citations, the ones a correct answer cites: the rules and context rules of a scenario or a deck question, the card of a factual question, the rule of a simple-rule question.
- `hand-check.jsonl`: about 150 questions, stratified by category and spread over groups, with a `review` for the human check of design doc §6.1.
- `manifest.json`: the counts per category and split, the seed, the model and its options, a hash of the prompts, the engine commit, hashes of the content, the rulebook and the engine's files, and every check that failed.

## Code

| Module | Role |
|---|---|
| `dataset/build.py` | The pipeline, from the engine's files to the version's files |
| `dataset/rulebook.py`, `dataset/content.py` | The rules with their sentences; the card names and keywords |
| `dataset/unanswerable.py`, `dataset/simple_rules.py` | The two categories this pipeline adds |
| `dataset/wording.py`, `dataset/prompts.py` | The rewording and its checks; every prompt, hashed into the manifest |
| `dataset/llm.py` | The model behind one interface: the OpenAI-compatible client, the disk cache, a fake for the tests and dry runs |
| `dataset/split.py`, `dataset/hand_check.py` | The split by group; the hand-check sample |

`tests/dataset/test_versions.py` checks every committed version (no group on both sides, counts, abstentions), so CI guards the data as well as the code.
