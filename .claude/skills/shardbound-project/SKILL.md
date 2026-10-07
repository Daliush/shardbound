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

## Design doc map

§0 glossary · §1 pitch · §2 game summary · §3 architecture (3.1 language split, 3.4 engine API) · §4 model choices · §5 AI modules (coach, Arbiter, opponents, Jev) · §6 evaluation (datasets, matrix, metrics, experiments, bot tournament) · §7 local training · §8 online and local demo · §9 dev workflow · §10 repository layout · §11 roadmap · §12 risks · §13 README checklist.

## Components

| Path | Stack | Role |
|---|---|---|
| `engine/core` | Java 21, no Spring (Jackson only) | Rules, legal actions, events with rule trace, player views, scenarios, determinization, random / greedy (later MCTS) bots |
| `engine/api` | Spring Boot | REST for resources (cards, decks, games), WebSocket protocol for live games; later MCP and gRPC |
| `ai/` | Python (uv, FastAPI) | Chat orchestrator, RAG, LoRA, LLM and Jev bots, eval harness |
| `frontend/` | Angular | Test UI first, polished UI later, results pages and answer comparator |
| `proto/` | Protobuf / gRPC | Contract between the engine and the Python players |
| `content/cards/` | JSON + JSON Schema | Single source for card data |

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
5. **Whatever the engine can check, it checks**: verdict, citations, step order, format. The LLM judge only grades tone and clarity, is calibrated on human labels, and comes from a different model family than the `api+rag` model.
6. **Isolate variables.** One open base model for the whole Arbiter matrix. LoRA variants differ only by their training data. 3 seeds per adapter, bootstrap confidence intervals, paired tests, temperature 0.
7. **LoRA learns how to arbitrate, not the cards.** Facts come from RAG. The patch experiment retrains nothing.
8. **Only host a model whose weights we change.** Only the LoRA Arbiter needs a GPU, and only locally.
9. **No AI online.** The site has precomputed results, an answer comparator and games against MCTS on Cloud Run CPU. The Arbiter runs locally through `docker compose --profile demo up`, which must work without a GPU.
10. **Jev is peripheral**: the opponent's System 1, the router, an optional guardrail and the reranker experiment. It sits behind a `DecisionModel` interface with a fallback, and nothing in the Arbiter, RAG, LoRA or judge depends on it.
11. **Destructive MCP tools are flagged statically** with `destructiveHint` and require user confirmation.

## Roadmap logic

The phases in `docs/design.md` §11 are ordered on purpose. Foundations come first (rules, content, an engine API serving its four consumers, a minimal test UI). AI work follows, because that is where the portfolio value is. The deck builder and the polished UI come last. A GPU is never used online.

To know where things stand, check the roadmap and the repository itself. Phase 1 (rulebook, card and deck formats, 30 cards + 1 token, two starter decks, content tests in CI) is done. Phase 2 is underway, fully specified in `specs/phase-2-engine.md` and built in five slices. As of 2026-10-07:

- slice 1 is done: `engine/core` plays full games for the rules of sections 1 to 7 and 9, the effects Damage, Destroy, Heal, Draw and Summon, with the scenario service, the random bot, fuzz, determinism and JSON round-trip tests;
- slice 2 is done: `engine/api` serves REST and the WebSocket protocol on versioned sessions built for several instances, and `frontend/` plays human vs bot and human vs human in the browser;
- slice 3 is done: every effect of section 8 but Link, with stat and cost auras, and the card text written from the card data, returned by `GET /api/cards` and shown by the client;
- next come the keywords and Link (slice 4), then determinization and the greedy bot (slice 5). Read the spec before working on either.

Engine layout, so new code lands in the right place: `content` (card and deck model, strict loader), `state` / `action` / `decision` / `event` / `view` (immutable API types), `resolution` (pending steps and queued triggers, stored in the state), `rules` (`GameEngine` facade, then one sub-package per rulebook area: `game` loop and state check, `setup`, `turn`, `play`, `combat`, `effect`, `aura`, `trigger`, `board`), `scenario`, `text` (describers and card text), `bot`, `json`. The game server is designed for several instances: no instance owns a game: sessions are saved with an optimistic lock, and notifications tell every instance that a game moved (spec §13.4). Its layers, checked by an ArchUnit test: `controller` (REST and WebSocket endpoints) → `domain` (business objects, services, ports; no transport, no JSON) ← `adapter` (the ports implemented, entity mapping) → `dao` (pure data, `*InMemory` now).

## Keeping the docs in sync

When a decision changes the design, update `docs/design.md`, including its changelog at the top. For rule changes, follow the `shardbound-rules` skill. Game and product decisions belong to the maintainer: propose with a recommendation instead of deciding alone.
