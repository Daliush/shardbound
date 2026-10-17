---
name: shardbound-pipeline-run
description: How to run the Arbiter's pipeline from end to end and check what each step produced — the evaluation dataset (scripts/build-dataset.sh), the chunks, the RAG index and the retrieval evaluation (scripts/evaluate.sh), and the report the game server serves. Says which steps a change calls for, the order that keeps every recorded result at a clean commit, what to compare after a run, and how to run it all in a worktree beside the maintainer's uncommitted work. Use this whenever the rulebook, a card, a recipe, the corpus or ai/rag changed and the dataset, the chunks, the index or the evaluation must follow, when asked to regenerate any of them, to run "the whole pipeline", to test it end to end, or to record a new evaluation — even when only one step seems to be needed.
---

# Running the Arbiter's pipeline

What each step does and why lives in `docs/design.md` §5.2 and §6, `ai/README.md` and the `shardbound-rag` skill. This skill is about running the steps and checking them.

## The steps

| Step | Command, from the root | Needs | Writes |
|---|---|---|---|
| Dataset | `bash scripts/build-dataset.sh <version> --base <previous>` | JDK 21, uv, `LUNA_BASE_URL`, `LUNA_MODEL`, `LUNA_API_KEY` | `evals/datasets/<version>/` |
| Chunks, index, evaluation | `bash scripts/evaluate.sh [retriever…] [--version <v>] [--split dev]` | JDK 21, uv, Docker running | `ai/.corpus/chunks.jsonl`, the database, `evals/reports/<v>/<split>/<timestamp>.json` |
| Served | the game server, `GET /api/evaluations/<v>/<split>` then `/<timestamp>` | the server running | nothing |

`evaluate.sh` runs `build-index.sh`, then `python -m rag evaluate` with its arguments: every retriever on the latest dataset's dev split by default. `--split test` is only for the published numbers, never to choose anything.

If `docker version` shows no server, ask the maintainer to start Docker Desktop. Do not reset it.

## What a change calls for

| What changed | Then |
|---|---|
| A rule's text (`docs/rules/`) | Its chunk: `evaluate.sh`. If the rule has a written question (`engine/dataset/src/main/resources/questions/rules.jsonl`), its `rule_text` and key facts must follow first, or the dataset build stops. |
| What the engine does | `./mvnw verify`. A new dataset version if answer keys change. |
| A card | The chunks and a new dataset version: factual questions and scenarios read the cards. |
| A recipe, or a written question | A new dataset version. |
| A retriever or a metric (`ai/rag`) | `evaluate.sh` alone. |
| An entity (`ai/rag/repository/entities.py`) | `docker compose down -v` from the root, then `evaluate.sh`: the tables are never altered, so the database starts empty and every chunk is embedded again. |

A built dataset version is frozen. A change makes a new version on the previous one (`build-dataset.sh v3 --base v2`): an unchanged question keeps its wording and its side of the split, so only new or changed questions go to Luna. `rag evaluate` reads the version that `ai/rag/__main__.py` names by default; move that default, and `CLAUDE.md`'s status, to the new version.

## The order that keeps results clean

The dataset's manifest records the engine commit and `sources_clean` (no change in `engine`, `content`, `docs/rules`, `ai`). An evaluation records the code commit, `-dirty` when a tracked file outside `evals/reports/` changed. So, one commit each:

1. Commit the change: rules, engine, tests, docs.
2. Build the dataset; commit it, with the CLI's new default version.
3. Run `evaluate.sh`; commit the report, and the numbers of the `shardbound-rag` skill if they moved.

## Checking a run

- **Dataset**: compare the engine's five files with the base version's, by id. Only the questions you expect should be new, gone or changed. "N reworded now" counts the new or changed templates; read each new wording against its template (the arrival order, a number, who acts). The manifest should say `"sources_clean": true`. `uv run pytest` in `ai/` checks every version.
- **Chunks**: copy `ai/.corpus/chunks.jsonl` before the run and diff it after. `qwen3-0.6b: N chunks embedded` should count exactly the chunks whose text changed; 0 when none did.
- **Evaluation**: compare with the previous report, retriever by retriever. Only questions whose wording or relevant chunks changed should score differently. From `ai/`:

  ```bash
  uv run python - <old report> <new report> <<'EOF'
  import json, sys
  def runs(path):
      return {run["layers"]["retriever"]: run for run in json.load(open(path, encoding="utf-8"))["runs"]}
  old, new = runs(sys.argv[1]), runs(sys.argv[2])
  for name, run in new.items():
      if name not in old:
          print(name, "is new")
          continue
      before, after = old[name]["scores"]["overall"], run["scores"]["overall"]
      old_ranks = {question["question_id"]: question["top_chunk_ids"] for question in old[name]["questions"]}
      moved = [question["question_id"] for question in run["questions"]
               if old_ranks.get(question["question_id"]) != question["top_chunk_ids"]]
      print(f'{name}: R@10 {before["mean_recall_at"]["10"]:.3f} -> {after["mean_recall_at"]["10"]:.3f}, '
            f'R@20 {before["mean_recall_at"]["20"]:.3f} -> {after["mean_recall_at"]["20"]:.3f}, '
            f'MRR {before["mean_reciprocal_rank"]:.3f} -> {after["mean_reciprocal_rank"]:.3f}, ranked differently: {moved}')
  EOF
  ```

  A question whose id changed shows as ranked differently. A run made only to check something leaves a report all the same: delete it unless it is meant to be committed.
- **Served**: start the server (from `engine/`, `./mvnw -pl api -am spring-boot:run`, or the `api` configuration of `.claude/launch.json`) and read `GET /api/evaluations/<v>/<split>`. It reads the reports folder at each request, so a new file needs no restart.

## In a worktree, beside the maintainer's work

When the main checkout holds his uncommitted work, run everything in a worktree on its own branch: `git worktree add -b <branch> <path> <start point>`.

- **A short path**: on Windows, some file names of the repository exceed the path limit under the scratchpad. `%TEMP%\sbN\Shardbound` fits.
- **A folder named `Shardbound`**: `docker compose` names its project after the folder, so this one reuses the `shardbound` database volume and embeds only what changed. Under another name it starts an empty database and embeds every chunk again.
- **`uv sync`** in its `ai/` before the first `uv run`.
- **Luna's cache**: copy `ai/.cache/llm` from the main checkout before building a dataset there. Without it Luna words the new questions a second time, and differently: nothing is worded twice.
- **The engine build**: one Maven run at a time per worktree, since they share `target/`.
- Afterwards, `git worktree remove <path>`; the branch keeps its commits.
