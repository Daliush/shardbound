# Shardbound

Open-source portfolio project: an invented trading card game used as a testbed for AI engineering (an MCP coach agent, a RAG and LoRA rules Arbiter, AI opponents, rigorous evaluation). Java engine and backend, Python AI layer, Angular frontend.

These instructions are for every coding agent working on the repository. `CLAUDE.md` imports them for Claude Code.

## Language

- Everything committed to this repository is in English: code, docs, comments, card data, commit messages.
- The maintainer writes to agents in French or English. Answer in the language of their message; write files in English.

## Where things are

- `docs/design.md`: design document (architecture, evaluation, roadmap, decisions).
- `docs/rules/`: game rules, the written source of truth. One file per section; start from `docs/rules/README.md`.
- `content/cards/`: one JSON file per card, plus the card schema and text templates (`content/cards/README.md`).
- `content/decks/`: one JSON file per deck, plus the deck schema (`content/decks/README.md`).
- `content/tests/`: tests for all the content above.
- `engine/`: the Java engine, a Maven multi-module build. `engine/core` is the rules engine (no Spring: content, state, rules, views, events, determinization, bots, scenarios; its README lists the entry points and documents the scenario service); `engine/api` is the Spring Boot game server (REST, WebSocket protocol, game sessions); `engine/dataset` writes every question of the Arbiter's evaluation dataset with its answer key: scenarios from recipes, factual and deck questions, and the simple-rule and unanswerable questions written once (its README says how to add a recipe); `engine/corpus` cuts the rulebook and the cards into the chunks of the Arbiter's RAG index (its README describes a chunk).
- `ai/`: the Python AI layer, a `uv` project. `ai/dataset` words the dataset's questions with Luna 6 and splits them into dev and test; `ai/rag` is the Arbiter's RAG, its retrievers and their retrieval metrics (`ai/README.md`); the eval harness comes next.
- `evals/datasets/<version>/`: the Arbiter's evaluation data, versioned and frozen. The final datasets are `dataset-dev.jsonl` (for tuning) and `dataset-test.jsonl` (for the published numbers); the other files are the engine's questions they come from, `manifest.json` and `hand-check.jsonl`. `evals/datasets/preview/` is a local output, ignored by git.
- `scripts/`: `build-dataset.sh` builds a dataset version from end to end, `build-index.sh` the RAG's index, `evaluate.sh` the index and then the retrieval evaluation.
- `frontend/`: the Angular test client (`frontend/README.md`).
- `specs/`: implementation specs. `specs/phase-2-engine.md` covers the engine, the game server and the test frontend; `specs/phase-2-examples.md` shows its payloads on a real situation.
- `.claude/skills/`: project knowledge, one folder per topic. Read the `SKILL.md` of the topic you work on; Claude Code loads it by itself.
  - `shardbound-project`: architecture, principles, roadmap.
  - `shardbound-rules`: how the rulebook works; answering and changing rules.
  - `shardbound-card-authoring`: designing and writing cards.
  - `shardbound-engine`: how the engine works and how to use it (API, resolution model, scenarios, bots, JSON).
  - `shardbound-engine-dev`: how to change the engine (workflow, design rules, recipes, tests, docs to keep in sync).
  - `shardbound-game-server`: how the game server works (sessions, protocol, several instances) and how to change it, frontend included.
  - `shardbound-content-sync`: deck rules and content formats are checked by both the Python content tests and the Java engine; what to change on each side.
  - `shardbound-rag`: the RAG Arbiter, built step by step with the maintainer: the plan, the chunks, the retrievers and their metrics, settled and open decisions.
  - `shardbound-pipeline-run`: running the Arbiter's pipeline end to end (dataset, chunks, index, evaluation), in which order, and checking each output.

## Commands

- Content tests (card and deck schemas, card and deck rules, text templates), from `content/`: `uv run pytest`. Run them after any change to `content/`. CI runs the same command (`.github/workflows/content.yml`).
- Engine build and tests, from `engine/`: `./mvnw verify` (`mvnw.cmd verify` on Windows). Run it after any change to `engine/` or `content/`, since the engine loads the content. CI runs it too (`.github/workflows/engine.yml`).
- Bot reports, from `engine/`, on demand: `./mvnw -pl core test -Preports` (a few minutes; greedy against random and the decks' balance, printed and written to `core/target/reports/`). The default build skips them.
- Whole game, from the root: `docker compose up --build` (client on http://localhost:4200, server on http://localhost:8080).
- Game server, from `engine/`: `./mvnw -pl api -am spring-boot:run` (http://localhost:8080).
- Evaluation dataset, from the root: `scripts/build-dataset.sh <version> --base <previous version>` runs the engine's generator, then the wording and split; it needs `LUNA_BASE_URL`, `LUNA_MODEL` and `LUNA_API_KEY` (`ai/README.md`), and `--fake` makes a dry run into `preview` without a key. A built version is frozen unless `--overwrite` is passed. The engine's step alone, from `engine/`: `./mvnw -q -pl dataset -am compile exec:java -Dexec.args=<version>`.
- The Arbiter's chunks, from `engine/`: `./mvnw -q -pl corpus -am compile exec:java` writes `ai/.corpus/chunks.jsonl` (not committed: it is derived from the rulebook and the cards), the handoff that only `python -m rag index` reads.
- The RAG's dense index, from the root: `scripts/build-index.sh` writes the chunks (Java), starts the database (`docker compose up -d --wait db`, Postgres with pgvector) and embeds the changed chunks (`uv run python -m rag index`); it needs JDK 21, uv and Docker, and the first run downloads the embedding model (`ai/README.md`).
- Retrieval metrics, from `ai/`: `uv run python -m rag evaluate --description "<why>" [retriever…] [--expanders expander…] [--rerankers reranker…]` scores every pipeline the techniques named make (each retriever, expanded or not, reranked or not; every technique by default, none with a flag alone), on the dev questions of the latest dataset, v2 unless `--version` says otherwise (Recall@10, Recall@20, MRR and the number of candidates, by category; the file also keeps the recall curve) and saves them as one new file, `evals/reports/<version>/<split>/<timestamp>.json`, each run named by its layers (`retriever`, `expand`, `rerank`, `answer`), which the game server serves to the benchmark explorer (`GET /api/evaluations/…`); every retriever reads the indexed chunks from the database, never `ai/.corpus/chunks.jsonl`, so build the index first. `--split test` only for the published numbers.
- Two evaluations compared, from `ai/`: `uv run python -m rag compare <recorded> <new> [--max-drop <points>]` prints each run's Recall@10 and Recall@20 in both and the questions it ranks differently; with `--max-drop`, it fails when a run's Recall@10 lost more points.
- The retrieval evaluation end to end, from the root: `scripts/evaluate.sh --description "<why>" [retriever…] [--expanders expander…] [--rerankers reranker…] [--version <v>] [--split dev|test]` runs `build-index.sh`, then `rag evaluate` with these arguments; it needs Docker running (`shardbound-pipeline-run` skill).
- Paths cited by the agents' instructions, from the root: `python3 scripts/check-doc-paths.py` (`python` on Windows) checks that every path `AGENTS.md`, `CLAUDE.md` and `.claude/` cite exists. Run it after renaming, moving or deleting a file, or editing those docs. CI runs it on every push (`.github/workflows/docs.yml`).
- AI tests, from `ai/`: `uv run pytest`. Run them after any change to `ai/` or `evals/`; they call no model. CI runs them (`.github/workflows/ai.yml`).
- Test client, from `frontend/`: `npm start` (http://localhost:4200, proxies `/api` and `/ws` to the server), `npm test` (unit tests, once), `npm run build`. Run the tests and the build after any change to `frontend/`. CI runs them (`.github/workflows/frontend.yml`).

## Commits

This repository is a portfolio: its history is read by recruiters. Follow a light version of Conventional Commits:

- Subject: `type(scope): summary`, in the imperative mood ("Add", not "Added"), no trailing period, 72 characters max.
- Types: `feat` (new game content or behavior), `fix`, `test`, `docs`, `ci`, `refactor`, `chore`.
- Scope: the area touched, e.g. `rules`, `cards`, `decks`, `content`, `engine`, `ai`, `skills`.
- Body: explain why the change is made and the decisions behind it; the diff already shows what changed. Wrap lines at 72 characters.
- One logical change per commit. New cards, a new format, and a docs-only update are separate commits. Tests go in the same commit as the code or content they cover, so every commit passes CI.
- Commits made with a coding agent end with a `Co-Authored-By:` trailer for it: the agentic workflow is part of what the project shows.
- `scripts/check-commit-message.sh` checks the subject and the line lengths. Turn it on once per clone with `git config core.hooksPath .githooks`, so that git runs it at each commit; CI checks every commit of a pull request (`.github/workflows/commits.yml`).

Example:

```
test(content): add card test suite and run it in CI

Validates every card against the schema, checks the rules the schema
can't express and makes sure every effect has a text template, so a
bad card can't reach the engine or the RAG index.
```

## Working agreements

- Game and product decisions belong to the maintainer. Propose with a recommendation, and number your questions so they can be answered point by point. If a gap must be filled, mark it *(proposed)*.
- Rule IDs are stable: never renumber them.
- Keep the docs in sync: a decision that changes the design updates `docs/design.md` (and its changelog); a rule change also updates `docs/rules/12-open-points.md`.
- Evaluation answer keys come from the engine or the card data, never from a hand or an LLM. An LLM may word a question, never its answer (`docs/design.md` §6.1). A test set is never used to tune anything.

## Status

This section is the only place that says where the project stands: the skills and the other docs point here.

Phase 2 of the roadmap (`docs/design.md` §11) is done, built in the five slices of `specs/phase-2-engine.md` §17. `engine/core` plays every rule, effect and keyword of the rulebook, with a rule trace, player views, a scenario service and two bots (`random`, and `greedy`, which never sees hidden cards). `engine/api` serves REST and the WebSocket protocol on sessions built for several instances. `frontend/` plays a game in the browser.

Phase 3, the RAG Arbiter (`docs/design.md` §5.2 and §6), is in progress, built step by step with the maintainer (`shardbound-rag` skill):

- Done: the evaluation dataset v2 (668 questions worded by Luna 6, split into dev and test, every answer key from the engine or the card data); the chunks (`engine/corpus`: 130 rules, 13 vocabulary terms, 48 cards, with their refs); the retrieval metrics; and the pipeline's first three steps: BM25 and dense retrieval fused by RRF, the refs expansion, and the MiniLM-L6 cross-encoder reranker. On v2 dev, the whole pipeline puts 72.7% of the cited chunks in the prompt's 10 (Recall@10). Each evaluation is saved as a file, and the client's benchmark lab shows every run.
- Next: nDCG@10, the refs attached again after the reranker as a variant, other rerankers; then generation (the prompt and the `{answer, citations, answerable}` output), the eval harness (§6.3) and the benchmark explorer (§8.2).
