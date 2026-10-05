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
- `specs/`: implementation specs. `specs/phase-2-engine.md` covers the engine, the game server and the test frontend.
- `.claude/skills/`: project knowledge for Claude Code.
  - `shardbound-project`: architecture, principles, roadmap.
  - `shardbound-rules`: how the rulebook works; answering and changing rules.
  - `shardbound-card-authoring`: designing and writing cards.

## Commands

- Content tests (card and deck schemas, card and deck rules, text templates), from `content/`: `uv run pytest`. Run them after any change to `content/`. CI runs the same command (`.github/workflows/content.yml`).

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

Phase 1 (rules and content) of the roadmap in `docs/design.md` §11. No game code yet; the only code is the content test suite in `content/tests/`.
