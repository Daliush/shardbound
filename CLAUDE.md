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
- `engine/`: the Java engine, a Maven multi-module build. `engine/core` is the rules engine (no Spring: content, state, rules, views, events, bots, scenarios); `engine/api` is the Spring Boot game server (REST, WebSocket protocol, game sessions).
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

## Commands

- Content tests (card and deck schemas, card and deck rules, text templates), from `content/`: `uv run pytest`. Run them after any change to `content/`. CI runs the same command (`.github/workflows/content.yml`).
- Engine build and tests, from `engine/`: `./mvnw verify` (`mvnw.cmd verify` on Windows). Run it after any change to `engine/` or `content/`, since the engine loads the content. CI runs it too (`.github/workflows/engine.yml`).
- Whole game, from the root: `docker compose up --build` (client on http://localhost:4200, server on http://localhost:8080).
- Game server, from `engine/`: `./mvnw -pl api -am spring-boot:run` (http://localhost:8080).
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

## Status

Phase 2 of the roadmap in `docs/design.md` §11, built in the five slices of `specs/phase-2-engine.md` §17.

- Slice 1 (engine foundation) is done: `engine/core` plays full games with the rules of setup, turns, Shards, zones, cards, combat with intercepts, triggers and the effects Damage, Destroy, Heal, Draw and Summon. Cards needing a later effect or keyword are not playable yet.
- Slice 2 (game server and test frontend) is done: `engine/api` serves REST and the WebSocket protocol, human vs bot and human vs human, on sessions built for several instances (in-memory adapters until deployment); `frontend/` plays a game in the browser.
- Next: slice 3, the other effects and the card text.
