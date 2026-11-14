---
name: shardbound-pipeline-run
description: How to run the Arbiter's pipeline from end to end and check what each step produced — the evaluation dataset (scripts/build-dataset.sh), the chunks, the RAG index and the retrieval evaluation (scripts/evaluate.sh), the report the game server serves, and the answers' evaluation (python -m arbiter.evaluation). Says which steps a change calls for, the order that keeps every recorded result at a clean commit, what to compare after a run, and how to run it all in a worktree beside the maintainer's uncommitted work. Use this whenever the rulebook, a card, a recipe, the corpus, ai/rag or ai/arbiter changed and the dataset, the chunks, the index or the evaluation must follow, when asked to regenerate any of them, to run "the whole pipeline", to test it end to end, or to record a new evaluation — even when only one step seems to be needed.
---

# Running the Arbiter's pipeline

What each step does and why lives in `docs/design.md` §5.2 and §6, `ai/README.md` and the `shardbound-rag` skill. This skill is about running the steps and checking them.

## The steps

| Step | Command, from the root | Needs | Writes |
|---|---|---|---|
| Dataset | `bash scripts/build-dataset.sh <version> --base <previous>` | JDK 21, uv, `LUNA_BASE_URL`, `LUNA_MODEL`, `LUNA_API_KEY` | `evals/datasets/<version>/` |
| Chunks, index, evaluation | `bash scripts/evaluate.sh --description "<why>" [retriever…] [--expanders expander…] [--rerankers reranker…] [--contexts context…] [--version <v>] [--split dev]` | JDK 21, uv, Docker running | `ai/.corpus/chunks.jsonl`, the database, `evals/reports/<v>/<split>/<timestamp>.json` |
| Served | the game server, `GET /api/evaluations/<v>/<split>` then `/<timestamp>` | the server running | nothing |
| Answer | from `ai/`, `uv run python -m arbiter ask "<question>"` | the index built, the database running, `CLAUDE_KEY` | `ai/.cache/arbiter/` |
| Answers' evaluation | from `ai/`, `uv run python -m arbiter.evaluation --description "<why>" [--contexts context…]` | the same | `evals/answers/<version>/<split>/<timestamp>.json` |

`evaluate.sh` runs `build-index.sh`, then `python -m rag evaluate` with its arguments: every pipeline the techniques make, each retriever expanded or not and reranked or not, each of these with and without each context, on the latest dataset's dev split by default (a flag alone, `--expanders`, `--rerankers` or `--contexts`, for none of them; an evaluation meant to be committed keeps the baseline's context, since the gate looks for a run of the baseline's exact layers; the first run of a reranker downloads its model). The description is required and kept in the evaluation: say why it is run ("Step 7: the reranker"), so that the benchmark explorer can show why each was run. `--split test` is only for the published numbers, never to choose anything. On the CPU, Qwen3-Reranker takes about 50 s a question, so an evaluation that runs it after every retriever takes hours: name the rerankers (`--rerankers minilm-l6`) to leave it out, or run them on a GPU (below).

If `docker version` shows no server, ask the maintainer to start Docker Desktop. Do not reset it.

The models, the embedding model and the rerankers, run on the CPU unless `RAG_DEVICE` names a GPU. On the maintainer's machine, the GPU build of torch lives in a second environment, `ai/.venv-gpu`, so a GPU run puts `UV_PROJECT_ENVIRONMENT=.venv-gpu UV_NO_SYNC=1 RAG_DEVICE=cuda HIP_VISIBLE_DEVICES=1` before `evaluate.sh` (`ai/README.md`); the evaluation records the device. A plain `uv run`, as the test hook does, syncs `ai/.venv` only, so it never touches a GPU run.

## What a change calls for

| What changed | Then |
|---|---|
| A rule's text (`docs/rules/`) | Its chunk: `evaluate.sh`. If the rule has a written question (`engine/dataset/src/main/resources/questions/rules.jsonl`), its `rule_text` and key facts must follow first, or the dataset build stops. |
| What the engine does | `./mvnw verify`. A new dataset version if answer keys change. |
| A card | The chunks and a new dataset version: factual questions and scenarios read the cards. |
| A recipe, or a written question | A new dataset version. |
| A retriever or a metric (`ai/rag`) | `evaluate.sh` alone. |
| A context (`ai/rag/contexts/`) | `evaluate.sh`, then the answers' evaluation, each at a clean commit: a context changes its retrieval runs and the prompt each answer is written from. |
| The prompt, a generator or an answer metric (`ai/arbiter`) | `uv run pytest` in `ai/`, then the answers' evaluation, at a clean commit. |
| An entity (`ai/rag/repository/entities.py`) | `docker compose down -v` from the root, then `evaluate.sh`: the tables are never altered, so the database starts empty and every chunk is embedded again. An embedding model's new format needs no reset: its vectors are kept under another key, and every chunk is embedded again. |

A built dataset version is frozen. A change makes a new version on the previous one (`build-dataset.sh v3 --base v2`): an unchanged question keeps its wording and its side of the split, so only new or changed questions go to Luna. `rag evaluate` and `arbiter.evaluation` read the version `LATEST_VERSION` names (`ai/dataset/paths.py`); move it, and `AGENTS.md`'s status, to the new version.

## The order that keeps results clean

The dataset's manifest records the engine commit and `sources_clean` (no change in `engine`, `content`, `docs/rules`, `ai`). An evaluation records the code commit, `-dirty` when a tracked file outside `evals/reports/` and `evals/answers/` changed. So, one commit each:

1. Commit the change: rules, engine, tests, docs.
2. Build the dataset; commit it, with the CLI's new default version.
3. Run `evaluate.sh`; commit the report, and the status of `AGENTS.md` if its numbers moved.
4. Run `python -m arbiter.evaluation`; commit its file, and the status if its numbers moved. A test refuses a tracked answers' file recorded `-dirty`.

A pull request that changes a file retrieval depends on (`ai/rag/gate/retrieval_inputs.py`) must commit an evaluation recorded at its last such change, from a branch up to date with main: CI's "Evaluation is fresh" refuses it otherwise, and `uv run python -m rag check-evaluation`, from `ai/`, says so before pushing.

## Checking a run

- **Dataset**: compare the engine's five files with the base version's, by id. Only the questions you expect should be new, gone or changed. "N reworded now" counts the new or changed templates; read each new wording against its template (the arrival order, a number, who acts). The manifest should say `"sources_clean": true`. `uv run pytest` in `ai/` checks every version.
- **Chunks**: copy `ai/.corpus/chunks.jsonl` before the run and diff it after. `qwen3-0.6b: N chunks embedded` should count exactly the chunks whose text changed; 0 when none did.
- **Evaluation**: compare with the previous report, run by run. Only questions whose wording or relevant chunks changed should score differently. From `ai/`, `uv run python -m rag compare <old report> <new report>` prints each run's context recall and number of chunks before and after (Recall@10 against a report recorded before context recall), and the questions it ranks differently.

  A question whose id changed shows as ranked differently. A run made only to check something leaves a report all the same: delete it unless it is meant to be committed.
- **Answers**: the command prints a row per category. Every reply should be a valid answer (a format failure is listed with the question); the cost and the latency should stay near the last file's; each metric says how many questions it scored, and a `–` means none. A rerun reads the cache, so it costs nothing and gives the same answers.
- **Served**: start the server (from `engine/`, `./mvnw -pl api -am spring-boot:run`) and read `GET /api/evaluations/<v>/<split>`. It reads the reports folder at each request, so a new file needs no restart.

## In a worktree, beside the maintainer's work

When the main checkout holds his uncommitted work, run everything in a worktree on its own branch: `git worktree add -b <branch> <path> <start point>`.

- **A short path**: on Windows, some file names of the repository exceed the path limit under the scratchpad. `%TEMP%\sbN\Shardbound` fits.
- **A folder named `Shardbound`**: `docker compose` names its project after the folder, so this one reuses the `shardbound` database volume and embeds only what changed. Under another name it starts an empty database and embeds every chunk again.
- **`uv sync`** in its `ai/` before the first `uv run`.
- **Luna's cache**: copy `ai/.cache/llm` from the main checkout before building a dataset there. Without it Luna words the new questions a second time, and differently: nothing is worded twice.
- **Claude's cache**: copy `ai/.cache/arbiter` too before asking the Arbiter there, or every answer is paid for and drawn again. A completion cached before step 9 has no time and cannot be read: clear such a cache once.
- **The engine build**: one Maven run at a time per worktree, since they share `target/`.
- Afterwards, `git worktree remove <path>`; the branch keeps its commits.
