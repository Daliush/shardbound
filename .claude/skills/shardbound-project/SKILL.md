---
name: shardbound-project
description: Big-picture guide to the Shardbound repository — what the project is for, how the Java engine and backend, the Python AI layer and the Angular frontend fit together, the engine API contract, the evaluation philosophy, the roadmap and the reasons behind each decision. Use this whenever you plan, scaffold or implement anything in the repo, decide where code should live, design the engine API, the eval harness, RAG or LoRA experiments, bots, MCP tools or deployment, or when the user asks about the plan, priorities or rationale.
---

# Shardbound project guide

## What this is

An open-source portfolio project for AI and agent engineering. An invented 1v1 trading card game is the testbed: since no LLM can know its rules, every correct answer has to come from RAG or fine-tuning, which keeps the measurements honest. The game is a pretext; the evaluation is the product.

- Full design: `docs/design.md`.
- Rules: `docs/rules/`, one file per section, indexed by `docs/rules/README.md` (see the `shardbound-rules` skill).
- Cards: see the `shardbound-card-authoring` skill.
- Engine: the `shardbound-engine` skill explains how it works and how to call it; `shardbound-engine-dev` explains how to change it.
- Game server and test client: the `shardbound-game-server` skill.
- The RAG Arbiter (chunks, retrievers, metrics, and how it is built with the maintainer): the `shardbound-rag` skill.

## Design doc map

§0 glossary · §1 pitch · §2 game summary · §3 architecture (3.1 language split, 3.4 engine API) · §4 model choices · §5 AI modules (coach, Arbiter, opponents, Jev) · §6 evaluation (datasets, matrix, metrics, experiments, bot tournament) · §7 local training · §8 online and local demo · §9 dev workflow · §10 repository layout · §11 roadmap · §12 risks · §13 README checklist.

## Components

| Path | Stack | Role |
|---|---|---|
| `engine/core` | Java 21, no Spring (Jackson only) | Rules, legal actions, events with rule trace, player views, scenarios, determinization, random / greedy (later MCTS) bots |
| `engine/api` | Spring Boot | REST for resources (cards, decks, games, the Arbiter's evaluations), WebSocket protocol for live games; later MCP and gRPC |
| `engine/dataset` | Java 21, on `engine/core` | Every question of the evaluation dataset with its answer key: recipes played by the engine, factual and deck questions, simple-rule and unanswerable questions written once |
| `engine/corpus` | Java 21, on `engine/core` | The Arbiter's knowledge base: the rules, vocabulary terms and cards as chunks with their refs, written to `ai/.corpus/chunks.jsonl` for the Python RAG index |
| `ai/` | Python (uv) | The dataset's wording (Luna 6) and dev / test split (`ai/dataset`); the RAG's retrievers and their retrieval metrics (`ai/rag`); later the chat orchestrator, LoRA, LLM and Jev bots, eval harness |
| `evals/datasets/` | JSONL | The dataset versions; the final files are `dataset-dev.jsonl` and `dataset-test.jsonl`, built by `scripts/build-dataset.sh` |
| `evals/reports/` | JSON | One file per evaluation, `<version>/<split>/<timestamp>.json`, written by `python -m rag evaluate`, served by `engine/api` to the benchmark explorer |
| `frontend/` | Angular | Test UI first, polished UI later, results pages and answer comparator |
| `content/cards/` | JSON + JSON Schema | Single source for card data |

A Protobuf / gRPC contract between the engine and the Python players comes with the AI opponents (phase 6).

Language rule: compute-heavy code and business logic go to Java; code that calls or evaluates models goes to Python.

## Principles, and why they exist

1. **The engine is the single source of truth.** It never decides anything: it applies the rules and lists legal actions. Arbiter answer keys, deck legality and bot moves all defer to it.
2. **The engine API serves four consumers, so design for all four, not just the UI.** Designing only for the UI means rewriting it later.
   - frontend;
   - bots: player view + legal actions → index of the chosen action. The view includes the public event history;
   - scenario service: board + action sequence → final state + rule trace;
   - MCTS: clone a state, and determinize one from a player's view.
3. **Rule trace.** Each resolution step emits the ID of the rule applied. This trace is the answer key for the Arbiter's citations and step order.
4. **MCTS must never clone the raw internal state.** It would see hidden cards and inflate its Elo. Use determinization: known cards stay fixed, the rest is sampled from the faction and neutral pool, respecting the 2-copy limit.
5. **Whatever the engine can check, it checks.** The engine writes each answer key: key facts (sentences from `EventDescriber`), essential rules and the trace. Code checks citations, step order and format; an extractor only answers closed questions ("does the answer state this key fact?"); the LLM judge only grades tone and clarity, is calibrated on human labels, and comes from a different model family than the `api+rag` model. The Arbiter returns one schema for every question, `{answer, citations, answerable}`, with no verdict field.
6. **Isolate variables.** One open base model for the whole Arbiter matrix. LoRA variants differ only by their training data. 3 seeds per adapter, bootstrap confidence intervals, paired tests, temperature 0.
7. **LoRA learns how to arbitrate, not the cards.** Facts come from RAG. The patch experiment retrains nothing.
8. **Only host a model whose weights we change.** Only the LoRA Arbiter needs a GPU, and only locally.
9. **No AI online.** The site has precomputed results, an answer comparator and games against MCTS on Cloud Run CPU. The Arbiter runs locally through `docker compose --profile demo up`, which must work without a GPU.
10. **Jev is peripheral**: the opponent's System 1, the router, an optional guardrail and the reranker experiment. It sits behind a `DecisionModel` interface with a fallback, and nothing in the Arbiter, RAG, LoRA or judge depends on it.
11. **Destructive MCP tools are flagged statically** with `destructiveHint` and require user confirmation.
12. **No leakage into published numbers.** The dataset is split by interaction (train / dev / test), cards are also held out between train and test, the RAG is tuned on dev only, and the prompt stays generic. One test set serves every configuration. Every RAG variant is benchmarked and shown in the benchmark explorer (`docs/design.md` §6.2, §8.2).

## Roadmap logic

The phases in `docs/design.md` §11 are ordered on purpose. Foundations come first (rules, content, an engine API serving its four consumers, a minimal test UI). AI work follows, because that is where the portfolio value is. The deck builder and the polished UI come last. A GPU is never used online.

Where things stand: the Status section of `AGENTS.md`, the only place that says it. Do not copy it into a skill.

## Code layout

Engine layout, so new code lands in the right place: `content` (card and deck model, strict loader), `state` / `action` / `decision` / `event` / `view` (immutable API types), `resolution` (pending steps and queued triggers, stored in the state), `rules` (`GameEngine` facade, then one sub-package per rulebook area: `game` loop and state check, `setup`, `turn`, `play`, `combat`, `effect`, `aura`, `trigger`, `board`), `scenario`, `text` (describers and card text), `determinization` (a whole game from one view), `bot` (the player contract, then one sub-package per bot: `random`, `greedy`), `json`. `engine/core/README.md` lists each package's entry point. The game server is designed for several instances: no instance owns a game: sessions are saved with an optimistic lock, and notifications tell every instance that a game moved (spec §13.4). Its layers, checked by an ArchUnit test: `controller` (REST and WebSocket endpoints) → `domain` (business objects, services, ports; no transport, no JSON) ← `adapter` (the ports implemented, entity mapping) → `dao` (pure data, `*InMemory` now).

## Keeping the docs in sync

When a decision changes the design, update `docs/design.md`, including its changelog at the top. For rule changes, follow the `shardbound-rules` skill. Game and product decisions belong to the maintainer: propose with a recommendation instead of deciding alone.
