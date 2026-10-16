# Shardbound

Open-source portfolio project: an invented trading card game used as a testbed for AI engineering (an MCP coach agent, a RAG and LoRA rules Arbiter, AI opponents, rigorous evaluation). Java engine and backend, Python AI layer, Angular frontend.

## Language

- Everything committed to this repository is in English: code, docs, comments, card data, commit messages.
- The maintainer writes to Claude in French or English. Answer in the language of their message; write files in English.

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
- `.claude/skills/`: project knowledge for Claude Code.
  - `shardbound-project`: architecture, principles, roadmap.
  - `shardbound-rules`: how the rulebook works; answering and changing rules.
  - `shardbound-card-authoring`: designing and writing cards.
  - `shardbound-engine`: how the engine works and how to use it (API, resolution model, scenarios, bots, JSON).
  - `shardbound-engine-dev`: how to change the engine (workflow, design rules, recipes, tests, docs to keep in sync).
  - `shardbound-game-server`: how the game server works (sessions, protocol, several instances) and how to change it, frontend included.
  - `shardbound-content-sync`: deck rules and content formats are checked by both the Python content tests and the Java engine; what to change on each side.
  - `shardbound-rag`: the RAG Arbiter, built step by step with the maintainer: the plan, the chunks, the retrievers and their metrics, settled and open decisions.

## Commands

- Content tests (card and deck schemas, card and deck rules, text templates), from `content/`: `uv run pytest`. Run them after any change to `content/`. CI runs the same command (`.github/workflows/content.yml`).
- Engine build and tests, from `engine/`: `./mvnw verify` (`mvnw.cmd verify` on Windows). Run it after any change to `engine/` or `content/`, since the engine loads the content. CI runs it too (`.github/workflows/engine.yml`).
- Bot reports, from `engine/`, on demand: `./mvnw -pl core test -Preports` (a few minutes; greedy against random and the decks' balance, printed and written to `core/target/reports/`). The default build skips them.
- Whole game, from the root: `docker compose up --build` (client on http://localhost:4200, server on http://localhost:8080).
- Game server, from `engine/`: `./mvnw -pl api -am spring-boot:run` (http://localhost:8080).
- Evaluation dataset, from the root: `scripts/build-dataset.sh <version> --base <previous version>` runs the engine's generator, then the wording and split; it needs `LUNA_BASE_URL`, `LUNA_MODEL` and `LUNA_API_KEY` (`ai/README.md`), and `--fake` makes a dry run into `preview` without a key. A built version is frozen unless `--overwrite` is passed. The engine's step alone, from `engine/`: `./mvnw -q -pl dataset -am compile exec:java -Dexec.args=<version>`.
- The Arbiter's chunks, from `engine/`: `./mvnw -q -pl corpus -am compile exec:java` writes `ai/.corpus/chunks.jsonl` (not committed: it is derived from the rulebook and the cards), the handoff that only `python -m rag index` reads.
- The RAG's dense index, from the root: `scripts/build-index.sh` writes the chunks (Java), starts the database (`docker compose up -d --wait db`, Postgres with pgvector) and embeds the changed chunks (`uv run python -m rag index`); it needs JDK 21, uv and Docker, and the first run downloads the embedding model (`ai/README.md`).
- Retrieval metrics, from `ai/`: `uv run python -m rag evaluate [retriever…]` scores every retriever, or the ones named, on the dev questions of the latest dataset, v2 unless `--version` says otherwise (Recall@10, Recall@20, MRR, by category) and saves them as one new file, `evals/reports/<version>/<split>/<timestamp>.json`, each run named by its layers (`retriever`, `answer`), which the game server serves to the benchmark explorer (`GET /api/evaluations/…`); every retriever reads the indexed chunks from the database, never `chunks.jsonl`, so build the index first. `--split test` only for the published numbers.
- The retrieval evaluation end to end, from the root: `scripts/evaluate.sh [retriever…] [--version <v>] [--split dev|test]` runs `build-index.sh`, then `rag evaluate` with these arguments; it needs Docker running.
- AI tests, from `ai/`: `uv run pytest`. Run them after any change to `ai/` or `evals/`; they call no model. CI runs them (`.github/workflows/ai.yml`).
- Test client, from `frontend/`: `npm start` (http://localhost:4200, proxies `/api` and `/ws` to the server), `npm test` (unit tests, once), `npm run build`. Run the tests and the build after any change to `frontend/`. CI runs them (`.github/workflows/frontend.yml`).

## Commits

This repository is a portfolio: its history is read by recruiters. Follow a light version of Conventional Commits:

- Subject: `type(scope): summary`, in the imperative mood ("Add", not "Added"), no trailing period, 72 characters max.
- Types: `feat` (new game content or behavior), `fix`, `test`, `docs`, `ci`, `refactor`, `chore`.
- Scope: the area touched, e.g. `rules`, `cards`, `decks`, `content`, `engine`, `ai`, `skills`.
- Body: explain why the change is made and the decisions behind it; the diff already shows what changed. Wrap lines at 72 characters.
- One logical change per commit. New cards, a new format, and a docs-only update are separate commits. Tests go in the same commit as the code or content they cover, so every commit passes CI.
- Commits made with Claude Code end with a `Co-Authored-By:` trailer for Claude: the agentic workflow is part of what the project shows.

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

Phase 2 of the roadmap in `docs/design.md` §11 is done, built in the five slices of `specs/phase-2-engine.md` §17.

- Slice 1 (engine foundation) is done: `engine/core` plays full games with the rules of setup, turns, Shards, zones, cards, combat with intercepts, triggers and the effects Damage, Destroy, Heal, Draw and Summon. Cards needing a later effect or keyword are not playable yet.
- Slice 2 (game server and test frontend) is done: `engine/api` serves REST and the WebSocket protocol, human vs bot and human vs human, on sessions built for several instances (in-memory adapters until deployment); `frontend/` plays a game in the browser.
- Slice 3 (effects and card text) is done: every effect of rulebook section 8 but Link (Sacrifice as a cost and an effect, Modify, Discard, Return to hand, Freeze, Gain Shards, Recall, stat and cost auras); `CardTextRenderer` writes each card's text from its data, `GET /api/cards` returns it and the client shows it.
- Slice 4 (keywords) is done: Echo, Fracture, Anchor with doom, Overcharge and Link, so every card of the catalog is playable; `RulebookCoverageTest` fails when a rule of sections 1 to 11 has no test. A Tide starter deck joins Ember's and Root's.
- Slice 5 (bots) is done: views carry the resolution in progress; the `Determinizer` builds a whole game from one player's view alone; `GreedyBot` plays the best board one action ahead and beats `RandomBot` in about 98% of games; `greedy` is offered by the server and the client; the scenario service is a documented public API (`engine/core/README.md`).
Phase 3, the RAG Arbiter, is designed (`docs/design.md` §5.2 and §6) and in progress:

- The evaluation scenarios are done: `engine/dataset` builds them from recipes, 25 interactions and 177 scenarios, each answer key reviewed against the rulebook; eight cards were added for them (47 cards + 1 token).
- The dataset v2 is built: 668 questions (177 scenarios, 214 factual, 49 deck building, 130 simple rule, 98 unanswerable), worded by Luna 6, in `evals/datasets/v2/dataset-dev.jsonl` and `dataset-test.jsonl`. It is v1 with players at 20 HP instead of 50 (rule 1.1): a new version keeps the previous one's wordings and split for every unchanged question, so only rule 1.1's answer key and four scenarios changed.
- The RAG pipeline (`docs/design.md` §5.2) is in progress, built step by step with the maintainer: `engine/corpus` writes the chunks (130 rules, 13 vocabulary terms, 48 cards) with their refs; `ai/rag` scores any retriever on the dev questions (Recall@10, what the prompt holds; Recall@20, what the reranker receives; MRR), with a random retriever as the floor; BM25 (bm25s) is the first retriever, with its variants; dense retrieval embeds the chunks with Qwen3-Embedding-0.6B into pgvector; each evaluation is saved as its own timestamped file and served by the game server (`GET /api/evaluations/…`). Next: RRF fusion, refs expansion, the reranker, then generation.
- Then: the eval harness (§6.3) and the benchmark explorer (§8.2).
