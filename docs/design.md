# Shardbound — AI / Agent Engineer portfolio project

> An invented trading card game, and a whole AI ecosystem around it:
> an agentic coach plugged in through MCP, a rules Arbiter built with RAG and LoRA,
> AI opponents (MCTS, and Jev + LLM), and an evaluation bench that rigorously compares every approach.
> Backend and engine in **Java**, AI layer in **Python**, frontend in **Angular**.
> Online demo with zero AI cost; the Arbiter is tested locally.

> **Changelog**
>
> **v2 (October 2026)** — main changes after review:
> - Foundations first: settled rules (§2.5), an engine whose API is designed for its four consumers (§3.4), a small test frontend. The deck builder and the polished frontend come after the AI work (§11).
> - The engine traces the rules it applies: the Arbiter's verdict, citations and steps are checked automatically. The judge only grades form (§6.3).
> - Statistical rigor: 3 seeds per adapter, confidence intervals, paired tests (§6.3).
> - The balance patch retrains nothing. LoRA learns how to arbitrate, not the cards (§5.2, §6.4).
> - MCTS with determinization, so it cannot cheat with hidden information. It is also the opponent in the online demo (§6.5).
> - No GPU online: a site with precomputed results and an answer comparator; the Arbiter runs locally (§8).
> - `frontier+rag` renamed to `api+rag`.
>
> **v2.1 (October 2026)** — game rules settled and written in the [rulebook](rules/README.md), one file per section. Section 2 below is only a summary: when they disagree, the rulebook wins.
>
> **v2.2 (October 2026)** — the project is open source: all repository content is in English. Project knowledge for Claude Code lives in `.claude/skills/`.
>
> **v2.3 (October 2026)** — card format defined in [`content/cards/`](../content/cards/README.md): one JSON file per card with a stable id, rules text generated from the data, game versions as git tags.
>
> **v2.4 (October 2026)** — faction identities set (Ember = attack, Tide = buffs and debuffs, Root = summoning), number scale adopted, first batch of 11 cards + 1 token.
> 
> **v2.5 (October 2026)** — second batch of 19 cards (30 cards + 1 token in total), deck format in [`content/decks/`](../content/decks/README.md) with two 30-card starter decks, Ember vs Root. Content tests run in CI.
>
> **v2.6 (October 2026)** — engine architecture decided: `engine/core` (domain, immutable state, no Spring) and `engine/api` (Spring Boot), REST for resources and a WebSocket protocol for live games, a minimal Angular `frontend/`. Implementation spec: [`specs/phase-2-engine.md`](../specs/phase-2-engine.md).
>
> **v2.7 (October 2026)** — rules clarified for the engine with the maintainer: 19 new rules (1.6, 3.8, 6.8, 6.9, 7.9, 7.10, 8.15–8.22, 9.11, 10.5, 10.6, 11.1.10, 11.2.7), and two rules reworded: Freeze lasts the current turn and the next one (8.10), and the non-active player also makes the choices effects ask of them (5.5.2). List in [`rules/12-open-points.md`](rules/12-open-points.md).
>
> **v2.8 (October 2026)** — the game server is designed for several Cloud Run instances: no instance owns a game, sessions sit in a shared store with versioned saves, updates go through a pub/sub (§8.2, spec §13.4).
>
> **v2.9 (October 2026)** — phase 2, slice 1: the engine core plays full games (setup, turns, Shards, zones, cards, combat and intercepts, triggers, five effects) with a rule trace, player views, a scenario service, a random bot and JSON states. Cards that need a later effect or keyword are not playable yet.
>
> **v2.10 (October 2026)** — phase 2, slice 2: the game server (REST, WebSocket protocol, versioned sessions with an outbox, tested as two instances sharing one store) and a minimal Angular client. A human plays a bot or another human in the browser; everything the client shows comes from the engine.
>
> **v2.11 (October 2026)** — phase 2, slice 3: every effect of the closed list but Link (Sacrifice as a cost and an effect, Modify, Discard, Return to hand, Freeze, Gain Shards, Recall, stat and cost auras), each with its rule tests, and card text written from the card data by the engine, checked against the card format's examples. The client shows each card's text and answers a choice of cards by clicking them. Keywords come next.
>
> **v2.13 (October 2026)** — phase 2, slice 5, and phase 2 is done: player views carry the resolution in progress, a `Determinizer` builds a whole game from one player's view alone (known cards kept, the rest drawn among the copies left), and `GreedyBot` plays one action ahead on that game, never on the real one; it beats the random bot in about 95% of games and is offered in the client. Measured with greedy against greedy, the decks are not balanced yet (Ember loses most of its games), reported for the maintainer. The scenario service is a documented public API for the phase 3 dataset generator.
>
> **v2.12 (October 2026)** — phase 2, slice 4: the five keywords in the engine (Echo with its order and target choices, Fracture step by step, Anchor with doom, Overcharge and its Shard lock, Link and its damage sharing), so every card is playable. A coverage test now fails when a rule of sections 1 to 11 has no test. Nine Tide cards and a Tide starter deck (39 cards + 1 token, three starter decks). Four rules settled with the maintainer along the way: 6.10 (a max defense below 0), 11.1.11 (an echo keeps a Sacrifice's count), 11.2.8 (a Fracture card pays its sacrifice cost at each step), and 5.4.3 reworded (the abilities it triggers resolve within the turn).
>
> **v2.14 (October 2026)** — phase 3 designed with the maintainer. The RAG pipeline: one chunk per rule, card or ruling, prefixed with its heading path; written cross-references followed after the fusion and before the reranker; BM25 and embeddings fused by RRF; one output schema and a generic prompt (§5.2). The evaluation dataset: Java recipes build the scenarios, the engine writes the key facts and the essential rules, the split is by interaction and cards are held out for LoRA (§6.1). Answers are graded on key facts instead of a verdict field (§6.3). Every RAG variant is benchmarked, and a benchmark explorer shows every precomputed run (§6.2, §8.2).
>
> **v2.15 (October 2026)** — the evaluation scenarios: `engine/dataset` builds them from 25 recipes, one per interaction, and exports 177 questions with their answer keys, each reviewed against the rulebook. Eight cards join the catalog (47 cards + 1 token) to open rules no card reached: an aura that ends, a temporary defense change, an "Attack" ability, two Echo attacks, a Sacrifice effect, plus a third Anchor unit and Fracture spell. Some questions ask whether a move is legal, answered from the legal actions (§6.1).
>
> **v2.16 (October 2026)** — the evaluation dataset v1: 668 questions in six categories, with two final files, `dataset-dev.jsonl` and `dataset-test.jsonl`. The engine writes every question and its answer key, the factual and deck-building ones included; the simple-rule and unanswerable questions are written once and checked at every build. Luna 6 rewords the questions, a second call checks each rewrite, and no question is worded twice: a new version keeps the previous wording of an unchanged question, and a group keeps its side of the split. `scripts/build-dataset.sh` builds a version from end to end (§6.1).
>
> **v2.17 (October 2026)** — the RAG's chunks: `engine/corpus` cuts the rulebook and the cards into 191 chunks (130 rules, 13 vocabulary terms, 48 cards) with their references, for the Python index. A rule's prefix is the titles of its sections, without its ID; cards and terms get none, and the text keeps the rulebook's Markdown. The vocabulary becomes chunks, the keyword examples move under the rule they illustrate, and a card's refs also cover its triggers and the token it summons (§5.2).
>
> **v2.18 (October 2026)** — the retrieval metrics and BM25: `ai/rag` scores any retriever on the dev questions, a question's relevant chunks being its citations, with a random retriever as the floor (§6.3). BM25 (bm25s, Lucene formula, English stemming) is the first retriever, with one variant per setting (§6.2). The prompt holds 10 chunks instead of 5-6: a chunk is about 40 tokens, and no question needs more than 10 rules (§5.2).
>
> **v2.19 (October 2026)** — dense retrieval: Qwen3-Embedding-0.6B embeds the chunks on CPU, and Postgres with pgvector stores them, the chunks in one table and their vectors per model in another; only changed chunks are embedded again. The embedding model is a registry entry, so another model is one more line (§5.2, §6.2).

> **v2.20 (October 2026)** — evaluations are stored for the benchmark explorer: each `evaluate` is one file, `evals/reports/<version>/<split>/<timestamp>.json`, never overwritten and with no index, holding the dataset, the time, the code commit and its runs, each named by its layers (`retriever`, `answer`). The Python side saves it behind a repository and the game server serves it through a DAO (`GET /api/evaluations/…`), a folder for now, read at each request, a bucket online with nothing else to change. The RAG reads its chunks from the database only, so a run can't mix two versions of the corpus (§6.2, §8.2).
>
> **v2.21 (October 2026)** — players start with 20 HP instead of 50, which is also their maximum (rule 1.1): at 50, games dragged on. The engine and the card guidelines follow. The evaluation dataset v2 is built on v1: rule 1.1's answer key changes and the four death-trigger scenarios played at full HP are now asked at 20 HP, reworded; every other question keeps its wording and side of the split. The RAG's chunks and index are rebuilt (§2.1, §6.1).
>
> **v2.22 (October 2026)** — RRF fusion: BM25 and dense retrieval merged by rank (k = 60). On v2 dev, Recall@20, what the reranker will receive, rises to 78.6% (dense alone 74.1%), while Recall@10 falls to 59.6% (62.3%). The card names found in the question come with the refs expansion, since only their refs can raise recall (§5.2, §6.2).
>
> **v2.23 (October 2026)** — the refs expansion: each card and term chunk carries its name, and the fused top 20 gains the chunks of the cards the question names (exact names, whole words) and the refs of all of them, one level deep. On v2 dev, the candidates hold 95.3% of the relevant chunks, 41 chunks on average, against 78.6% in the top 20; the 7 interaction questions go from 0% to 89.8%. Since what the reranker receives is longer than any cutoff, the evaluation adds the candidate recall and the number of candidates (§5.2, §6.2, §6.3).
>
> **v2.24 (October 2026)** — evaluations explain themselves: each says why it was run (a required description) and each run what its retriever does, written by the retriever from its settings. Each run also keeps the recall curve, to compare retrievers at the same number of chunks, and each question the rank of each of its relevant chunks (§6.3, §8.2).

---

## 0. Glossary (read first)

| Term | Meaning in this project |
|---|---|
| **Engine** | Applies the rules, lists legal moves, resolves actions. It never decides anything. It is the project's **single source of truth**. |
| **Bot** | An artificial player: it picks a move among the legal moves provided by the engine. `random`, `greedy`, `mcts`, Jev and LLM are all bots. |
| **Tournament** | A series of N games between two bots (`simulate_matches`, round-robin Elo). |
| **MCTS** | *Monte Carlo Tree Search*. A bot that, to choose **one** move, simulates hundreds of imaginary games and keeps the move with the best win rate. |
| **Determinization** | Replacing hidden information (opponent's hand, deck order) with a plausible guess before simulating. Prevents MCTS from cheating. |
| **HP / Defense** | **HP**: a player's hit points. **Defense**: a unit's hit points. |
| **Coach** | Agentic chatbot that acts on the app through MCP (collection, decks, simulations). |
| **Arbiter** | Chatbot that answers rules questions, like an official rules referee at a tournament. Exists in several versions (RAG, LoRA, RAG + LoRA…) compared by the evaluation matrix. |
| **LoRA Arbiter** | Version of the Arbiter whose model was fine-tuned with a LoRA adapter. |
| **Answer key** | Reference answer computed by the engine: the key facts of the outcome, the essential rules and the full rule trace (§6.1). |
| **Interaction** | An abstract combination of mechanics, e.g. an Anchored unit hit by Destroy. One interaction gives many scenarios. The dataset is split by interaction (§6.1). |
| **Scenario** | A concrete game: cards, board, moment of the turn, moves played. Built by a recipe, played by the engine's scenario service. |
| **Recipe** | Java code, one per interaction, that builds its scenarios (cards filtered from their data × variants), names the moves and choices, says who asks the question and which events count (§6.1). |
| **Key fact** | A sentence of the answer key, written by the engine from an event or a legal action, e.g. "Root Sentinel is anchored: it is not destroyed." The Arbiter's answer must state it. |
| **Benchmark explorer** | Page of the site where a visitor sets every parameter (configuration, RAG variant, dataset version) and sees the matching precomputed results, down to each question (§8.2). |
| **Judge (LLM-as-judge)** | Evaluation only: a large model called through an API, **not trained**, given a grading rubric, the question, the engine's answer key and the Arbiter's answer. It grades the Arbiter, **never the engine**. Unrelated to the Arbiter itself. |
| **System 1 / System 2** | Architecture of the AI opponent: fast decisions (Jev) / strategic planning (LLM). |
| **RAG** | A retrieval step **before** calling the model: relevant passages are fetched and added to the prompt. The model is not modified. RAG involves no training. |
| **LoRA** | Fine-tuning that trains small matrices grafted onto the model's weights. Requires access to the weights. The project's variants (knowledge, RAFT, format) use the same technique: **only the training data changes**. |
| **RAFT** | *Retrieval-Augmented Fine-Tuning* (Berkeley, 2024): LoRA trained with retrieved context that includes misleading passages ("distractors"). |
| **Seed** | The random seed of a training run (matrix initialization, example order, dropout). Two different seeds give two slightly different adapters. |
| **`api+rag`** | Reference configuration: a commercial model called through an API (e.g. Claude Haiku) with the same RAG pipeline as the others. Acts as the ceiling. |

---

## 1. Pitch

Shardbound is a 1v1 card game with its own cards, keywords and interaction rules. It is not meant to be a commercial game: the game is a **testbed** to demonstrate the core skills of an AI engineer.

What the repository demonstrates:

- **Agentic AI**: a coach that acts on the app through an MCP server (collection, decks, simulations), and can consult the Arbiter as a tool.
- **RAG**: an Arbiter that answers rules questions from the rulebook and the card texts.
- **LoRA fine-tuning**: an open model specialized to arbitrate in a strict format. It learns how to arbitrate, not the cards.
- **Rigorous evaluation**: ground truth computed by the engine, quantified comparison of Base / RAG / LoRA / RAG+LoRA / RAFT against a reference API model, with confidence intervals.
- **AI opponents**: an MCTS in Java (the online demo's opponent) and a System 1 / System 2 architecture (Jev + LLM), ranked by Elo.
- **A justified polyglot architecture**: Java where computation and business logic matter, Python where models are called and evaluated.
- **A frugal online demo**: precomputed evaluation results, an answer comparator and games against the algorithmic bots, with zero AI cost. The Arbiter is tested locally.
- **Agentic development workflow**: the app is built with coding agents, and evals are wired into CI.

Why an invented game? Because a base LLM **cannot know** the rules. Every correct answer must come from RAG or LoRA, which keeps the measurements honest. The game also allows "balance patches", perfect for testing knowledge freshness.

---

## 2. The game

**Principle: no clear rules, no game.** Every rules ambiguity becomes noise in the evaluation. The engine is the reference specification, and the rulebook is checked against it by tests.

**The complete rules live in the [rulebook](rules/README.md)** (`docs/rules/`, one file per section), with a stable identifier per rule. This section is only a summary.

### 2.1 Core rules (summary)

- 2 players, 20 **HP** each (also the maximum). Units have **defense** instead.
- 30-card deck: a single faction + neutral cards, at most 2 copies of a card. Opening hand of 5 cards, one mulligan allowed.
- **Information**: each player knows the opposing **faction**, but neither the opposing decklist nor hand.
- Resource: **Shards**. +1 max Shard per turn (cap 10). Shards pay for playing cards **and** for attacking.
- Board: up to 6 units and 3 relics. Hand: up to 10 cards.
- Card types: **Units** (defense + one or two attack abilities, each with its own cost), **Spells** (immediate effect), **Relics** (permanent abilities, cannot be attacked).
- **Combat**: once per turn, a unit attacks by paying the cost of one of its attack abilities. A targeted attack hits an enemy unit; the player can only be attacked when they have no unit left. Some attacks have no target and only buff their own side. The defender can **intercept**: redirect the attack to another of their units. No retaliation. Spells, however, can target the player directly.
- During the opponent's turn you play no cards: you can intercept, and you make the choices that effects ask of you (an echo's target, a discard).
- **Victory**: bring the opponent to 0 HP. An empty deck does not lose the game, but each draw from an empty deck deals increasing **fatigue** (1, 2, 3… HP). Technical limit: the game is a draw after 50 turns per player.

### 2.2 Factions (3 + neutral)

| Faction | Identity | Play style |
|---|---|---|
| Ember | Fire, sacrifice | **Attack**: high damage, direct damage to the player, sacrifice |
| Tide | Water, manipulation | **Buffs and debuffs** |
| Root | Nature, growth | **Summoning**: tokens and sturdy units |
| Neutral | — | Utility cards, playable in any deck |

Target: **~80 cards** at launch, described in JSON validated by a **JSON Schema** (single source of truth for the Java engine, the docs and the Python datasets).

### 2.3 Invented keywords (where LLMs struggle)

*Summary; full definitions in the rulebook's keyword files (`docs/rules/11-*.md`).*

- **Echo X**: a passive of an attack ability. When the unit dies (not when it is returned to hand), that attack is replayed for free at X% of its power. An echo cannot be intercepted. X can exceed 100 (Echo 300). Echo chains are possible.
- **Fracture N**: a spell in N steps (2 to 5), each with its own cost and effect. Each time it is played, it applies the next step and returns to hand. The current step is not displayed: the opponent has to remember it.
- **Anchor**: until the start of its controller's next turn, the unit cannot leave the board. If it drops to 0 defense, it becomes doomed and is only destroyed at the end of that turn, unless it was healed.
- **Overcharge**: optional. The card costs 2 Shards less, but 2 Shards are locked on the next turn. Stackable.
- **Link**: created by neutral spells, between any two units, allied or enemy. Linked units share damage (half rounded up for the unit that was hit). One link per unit; it breaks when either unit leaves the board.

**Interactions** between keywords (e.g. a unit *Linked* to an *Anchored* unit hit by a destruction spell) naturally produce hard multi-step questions, ideal for evaluation.

**Naming note.** Keyword names should not collide with known games' keywords when the meaning is close, or a base LLM brings in its prior. "Overcharge" was chosen over "Overload" for that reason (Hearthstone's Overload is very close). "Echo" exists in Magic with a different meaning: worth watching in the evals (prior interference).

### 2.4 Content deliverables

- `content/cards/<faction>/<slug>.json`: one file per card, with a stable id (`ember.ash-warden`), validated by `card.schema.json`. The rules text is generated from the data with `text-templates.json`, never written by hand. See [`content/cards/README.md`](../content/cards/README.md).
- `docs/rules/`: the rulebook in Markdown (partly generated from the JSON, partly written). Each rule has a stable identifier (e.g. `11.3.2`), reused by the engine's trace.
- `content/decks/<id>.json`: decks (card ids + counts), validated by `deck.schema.json` and by tests of the deck-building rules. See [`content/decks/README.md`](../content/decks/README.md).
- `docs/rulings/`: FAQ of edge cases ("rulings"), as in real TCGs.
- `docs/patches/`: patch notes. Each game version is a git tag (`patch-1.0`, `patch-1.1`…).

**Each ruling has three uses, from a single source:**

1. documentation (edge-case FAQ);
2. a JUnit test of the engine;
3. an evaluation question, with its answer key computed by the engine.

### 2.5 Rules questions

The open questions of v2 are settled in the rulebook. Anything still open or only proposed is listed in [`rules/12-open-points.md`](rules/12-open-points.md). The number scale for cards is a design guideline in [`content/cards/README.md`](../content/cards/README.md); cards that turn out unbalanced are fixed through patches.

---

## 3. Architecture

### 3.1 Language split principle

> **What is compute-intensive or carries business logic goes to Java. What calls or evaluates models goes to Python.**

| Component | Language | Rationale |
|---|---|---|
| API, auth, collection, decks, boosters | Java (Spring Boot) | Classic business logic, credible in an enterprise context |
| Game engine | Java (pure module, no Spring) | Typed modeling (records, sealed interfaces, exhaustive pattern matching); embedded directly by the backend for human vs bot games |
| Random / greedy / MCTS bots | Java (inside the engine) | MCTS simulates hundreds of games per decision: this is where speed matters. It is also the online demo's opponent, at zero AI cost |
| MCP server | Java (official MCP SDK / Spring AI) | Thin layer over existing services, same process as the backend |
| Engine ↔ remote players contract | Protobuf / gRPC | One contract, code generated for Java and Python |
| Chat orchestrator, router | Python | Agentic ecosystem, Jev integration (langchain-typesafe) |
| RAG | Python | The served pipeline and the evaluated pipeline must be **the same** |
| LoRA (training + serving) | Python | Unsloth / PEFT / vLLM |
| LLM and Jev bots | Python | One API call per decision: the network hop to the engine is negligible compared to model latency |
| Eval harness, datasets, tournaments | Python | Evaluation and analysis ecosystem |
| Frontend | Angular | Consistent with a Java profile, RxJS fits the game WebSocket stream. Small test frontend first, polished frontend later |

**What Java performance really brings (and what it doesn't)**:

- Games played by **algorithmic** bots (random, greedy, MCTS) run entirely inside the engine, with no network round trip: a few milliseconds per game (more for MCTS, depending on its thinking budget). This is the default mode of `simulate_matches`.
- Games played by **model-based** bots (Jev, LLM) are bound by API latency and cost, whatever the engine's language. They are reserved for tournaments run as manual or nightly jobs, with a capped number of games.
- The performance benefit therefore rests mostly on **MCTS**, which is the reference baseline in the Elo ranking. Without MCTS, a Python engine would be defensible; the engine stays in Java mainly for typed modeling and because the Java backend embeds it directly.

### 3.2 Diagram

```mermaid
flowchart LR
    UI[Angular frontend<br/>game, results, chats] -->|REST + WebSocket| API

    subgraph JAVA[Java — Spring Boot]
        API[Backend API<br/>auth, collection, decks, games]
        ENG[Game engine<br/>pure module, deterministic<br/>scenarios + rule trace]
        ABOTS[Random / greedy / MCTS bots]
        MCP[MCP server]
        GRPC[gRPC server<br/>remote players + scenarios]
        API --> ENG
        ENG --> ABOTS
        MCP --> API
        GRPC --> ENG
    end

    subgraph PY[Python]
        CHAT[Chat orchestrator<br/>coach + Arbiter + router]
        RAG[RAG pipeline<br/>hybrid + reranker]
        MBOTS[LLM / Jev bots]
        EVAL[Eval harness<br/>datasets, tournaments, judge]
    end

    subgraph GPU[Local only — own or rented GPU]
        LORA[vLLM<br/>open model + LoRA adapters<br/>CPU demo: Ollama / llama.cpp]
    end

    API -->|chat proxy, centralized auth| CHAT
    CHAT -->|MCP client| MCP
    CHAT --> RAG
    CHAT --> LORA
    CHAT --> APIMODEL[API model<br/>e.g. Claude Haiku]
    MBOTS -->|gRPC| GRPC
    MBOTS --> JEV[Jev — System 1]
    MBOTS --> APIMODEL
    EVAL -->|gRPC| GRPC
    EVAL --> RAG
    EVAL --> LORA
    API --> DB[(Postgres + pgvector)]
    RAG --> DB
    OBS[Langfuse / OTel] -.-> CHAT
    OBS -.-> MBOTS
    OBS -.-> API
```

### 3.3 Detailed stack

| Layer | Choice | Notes |
|---|---|---|
| Backend language | Java 21 (LTS) | Records, sealed interfaces, pattern matching, virtual threads |
| Java build | Maven multi-module with the Maven Wrapper | `engine/core` (domain, no Spring) and `engine/api` (Spring Boot); a `repository` module later |
| Backend | Spring Boot | REST, WebSocket, security, JPA |
| Game engine | Pure Java, no framework dependency | Deterministic (seed), lists **legal actions**, emits the **rule trace** |
| MCP | Java MCP SDK / Spring AI MCP Server | HTTP transport, called by the Python orchestrator |
| Cross-language contract | Protobuf + gRPC (generated with `buf`) | Bidirectional streaming for remote players |
| Database | Postgres + pgvector | One database for business data and embeddings |
| Migrations | Flyway | Java side, owns the business schema |
| Frontend | Angular (standalone components, signals) + Tailwind | Small test frontend, then board, deck builder, chat panels, results pages |
| Python services | FastAPI, dependencies managed with `uv` | AI service exposed to the backend |
| RAG | BM25 (in memory) + embeddings (pgvector), fused by RRF, then a cross-encoder reranker | One chunk per rule / card / ruling (§5.2) |
| LoRA | Unsloth or PEFT (QLoRA) | Open model from 1.5B to 8B depending on available VRAM |
| LoRA serving | vLLM (local) | Multi-LoRA on a single base model, for evaluation |
| Local demo mode | Ollama or llama.cpp | Small quantized model + LoRA adapter, runs without a dedicated GPU |
| API model | Claude Haiku (or equivalent) | Coach, bot's System 2, `api+rag` reference |
| Judge | API model from a **different family** than the `api+rag` model | Avoids self-preference bias |
| Question paraphraser, key-fact extractor | API model from a family that no evaluated configuration uses (Luna 6) | Writes the questions' wording, checks the key facts (§6.1, §6.3) |
| Fast decisions | Jev (TypeSafe AI) | Behind an abstract interface, with a fallback |
| Observability | Langfuse + OpenTelemetry | End-to-end Java ↔ Python traces |
| Tests | JUnit 5 + AssertJ (Java), pytest (Python), Playwright (frontend, optional) | Rulings are JUnit tests |
| Local infra | Docker Compose | `docker compose up` must be enough |
| Deployment | Static site + Google Cloud Run CPU (scale to zero) | No GPU and no model call online |

### 3.4 The player protocol and the engine API (architectural keystone)

The engine does not know **who** is playing. It exposes a single protocol:

1. The engine sends the **state** (the player's view) + the list of **legal actions**.
2. The player answers with the index of the chosen action.

A human in the browser, the Java MCTS and the Python Jev bot are all "players" plugged into this same protocol:

- **local** players (Java bots): direct method call through a `Player` interface;
- **remote** players (Python bots): bidirectional gRPC stream;
- **human** players: WebSocket through the backend.

**The engine API is designed for its four consumers, not for the frontend.** If it only served the frontend at first, part of it would have to be rewritten later. It is done when it serves these four uses:

| Consumer | Needs |
|---|---|
| Frontend (human) | Player view, legal actions, play an action, event stream |
| Bots (player protocol) | The same protocol: player view + legal actions → index of the chosen action. The view includes the **history of public events**, so a bot can remember what it has seen (e.g. the step of a Fracture) |
| Scenario service (evaluation) | A board + a sequence of actions → exact final state + **trace of the rules applied** |
| MCTS | Clone a state; generate a plausible full state from a player's view (determinization, §6.5) |

**The rule trace.** At each resolution step, the engine emits an event carrying the identifier of the rule applied, for example `[11.3.2 Anchor] Root Sentinel cannot leave the board`. This trace is the answer key for the Arbiter's citations and step order (§6.3), and it makes debugging the engine easier.

The **scenario service** gives the dataset generator the **computed ground truth** for interaction questions, instead of having an LLM write it: Java recipes build the scenarios and export them with their answer keys, and Python writes the questions' wording (§6.1).

---

## 4. Model choices

### 4.1 RAG vs LoRA: what changes technically

- **RAG**: a retrieval step that happens **before** the model call. Relevant passages are fetched (vector search + BM25 + reranking, not just a vector database), pasted into the prompt, and the whole is sent to the model. The model is not modified: **any model works**, open or closed, local or through an API.
- **LoRA**: small matrices grafted onto the model's weights are trained. The weights are needed **at training time** (to compute the matrices) and **at inference time** (to load the adapter next to the base model). Hence an **open-weights model is mandatory**. Impossible with Claude Haiku, whose weights are not public.
- The real distinction is not "local vs API" but **"who holds the weights"**. A LoRA model can perfectly well be called through an API, as long as that API is served by someone who holds the weights: you (vLLM on your GPU), or a provider willing to host your adapter.

Analogy: RAG is letting the student open the book during the exam; any student can do it. LoRA is making the student study before the exam; you need access to their brain. RAG + LoRA is the student who studied the method and also has the book open.

Resulting rule: **we only host a model ourselves if we modify its weights.** Everything that goes through the prompt, RAG or tools works with a standard API model.

### 4.2 Two tiers of models

**Experimental tier — a single open model for the whole matrix.**
To isolate the effect of a technique, only one thing varies at a time: same base model, same prompt, same test set. Only the context (RAG or not) and the weights (LoRA adapter or not) change. Otherwise we measure the difference between two models, not between two techniques.
Candidates: open models from 1.5B to 8B (Qwen, Llama families, or small distilled DeepSeek versions; the large DeepSeek models are far too heavy for LoRA at this scale). Final choice at project start.

**Reference tier — an API model.**
A model like Claude Haiku serves three purposes:

- the **MCP coach** (tool-call reliability is the strength of large models, and small open models are noticeably weaker at it);
- the AI opponent's **System 2**;
- the **`api+rag`** reference configuration of the matrix, which acts as the ceiling.

The most interesting question of the project follows: **does a small open model with LoRA + RAG get close to Haiku + RAG, and at what cost and latency?**

| | Small model | API model |
|---|---|---|
| Model | 3B, open | Haiku |
| LoRA | Yes, trained by us | No, impossible |
| RAG | Yes | Yes, the same pipeline |
| Hosting | Local | Paid API |

LoRA is only applied on one side, on purpose: the question is whether fine-tuning lets a small model catch up with a large model that only has RAG.

### 4.3 Who needs a GPU?

| Component | Model | Weights modified? | Hosting |
|---|---|---|---|
| MCP coach | API (e.g. Haiku) | No | Regular API call (local only) |
| RAG Arbiter | API or open model | No | Regular API call |
| **LoRA Arbiter** | Open model + adapter | **Yes** | **Local**: own (or rented) GPU for training and evaluation; quantized model on CPU for the demo |
| RAG + LoRA Arbiter | Open model + adapter | **Yes** | **Local**, same |
| Bot's System 2 | API | No | Regular API call |
| Bot's System 1, router | Jev | No | TypeSafe API |
| Judge (evaluation) | API, different family | No | Regular API call |

**Only the LoRA Arbiter needs a GPU, and only locally.** Nothing is served on a GPU online (§8).

---

## 5. AI modules

### 5.1 The coach (MCP agent)

The coach knows nothing about the game and does not need to: **all knowledge comes from the tools, all validation comes from the Java engine**. The model only brings reasoning and action sequencing. That is why a standard API model is enough, without fine-tuning.

The coach runs locally (API key required): it is not exposed in the online demo.

MCP tools exposed by the Java server:

| Tool | Description | Risk |
|---|---|---|
| `search_cards` | Search by faction, cost, keyword, text | Read |
| `get_collection` | Cards owned by the user | Read |
| `create_deck` / `update_deck` | Create or modify a deck. The backend rejects an illegal deck with an explicit error, which the coach fixes | Write |
| `delete_deck` | Delete a deck | **Destructive** |
| `simulate_matches` | Run N games deck A vs deck B. **Greedy or MCTS bots by default** (fast, free, reproducible); Jev/LLM bots optional, capped at a small N | Compute |
| `get_match_stats` | Win rate, Shard curve, key cards | Read |
| `open_booster` | Open a booster (virtual currency) | Write |
| `ask_rules` | Ask the RAG Arbiter a rules question | Read |

Typical loop for "Build me an aggressive Ember deck":

1. `get_collection` → which cards the user owns.
2. `search_cards` → read the low-cost Ember and neutral cards.
3. `ask_rules` if a synergy is unclear ("does Link work well with Echo?").
4. `create_deck` → on a legality error, fix and retry.
5. `simulate_matches` against the reference deck, read the stats, adjust.

The `ask_rules` tool makes the Arbiter **a tool of the coach**: a demonstration of agent composition, with no extra hosting.

**Guardrail**: destructive tools are statically flagged (MCP annotation `destructiveHint`) and require user confirmation before running. Since the tool list is known, a static rule is more reliable than a classifier. Optionally, Jev (*noul* mode, §5.4) detects a tool call the user did not explicitly ask for.

### 5.2 The Arbiter

In real TCGs, a rules referee settles rules disputes at tournaments. Here, the Arbiter is a chatbot: the player asks it a rules question, and it answers by citing the rules.

Example question: "On my last turn, I played my Root Sentinel, which has Anchor, and linked it to my Bramble Warden. During their turn, my opponent casts Tempest, a neutral spell that destroys all units. What happens?"

Expected answer, as the user reads it (the `answer` field of the output, see below):

```
Rules cited: 11.3.1, 11.3.2, 11.3.3 (Anchor), 11.5.3, 11.5.4 (Link)
Resolution:
1. Tempest tries to destroy all units.
2. The Sentinel is protected by Anchor until the start of my next turn:
   it cannot leave the board. Only that part of Tempest is ignored;
   the rest of the spell applies.
3. The Warden is destroyed. It is linked to the Sentinel, but Link only
   shares damage, not destruction.
4. The Warden left the board: the link is broken.
Verdict: the Warden is destroyed, the Sentinel stays on the board and is no longer linked.
```

*In v1 this example was inconsistent: without a response window, Anchor never protected during the opponent's turn, and the deck mixed two factions. With the settled rules, the original situation becomes valid.*

The Arbiter exists in several versions, and **this is exactly what the evaluation matrix compares** (§6.2).

**RAG version**: answers from `docs/rules`, `docs/rulings` and the card texts. Each brick below is a baseline, and its variants are benchmarked (§6.2).

- **Chunks**: one rule = one chunk (its stable ID), with its items and examples, in the rulebook's Markdown (its bold marks a rule's name, a defined term or an emphasis); one card = one chunk (what it is, then the text written by `CardTextRenderer`, not the raw JSON); one vocabulary term = one chunk (some terms, such as owner, are defined nowhere else); one ruling = one chunk. A rule rarely names its subject ("While protected, it…"), so a parser prefixes it with its heading path, `[11.3 Anchor]` or `[5. Game sequence > 5.4 End of turn (in this order)]`; a card or a term names its own subject and gets no prefix. The ID stays out of the text: no question names a rule number, and the prompt's tags carry it. No model is involved. A chunk is stored as `{id, kind, name, text, refs, source, hash, vector}`: only `text` is embedded, indexed and shown to the model, the other fields serve the code. `engine/corpus` (Java) writes the chunks, since the rulebook parser and the card text are there; the Python index embeds and stores them.
- **Cross-references (`refs`)**, computed at indexing: the rule numbers written in a rule's text, `(5.4.3)` or "rule 9.8", found by a regex; for a card, read from its data, never from its words, through a mapping table: a keyword as its whole section (`anchor` → 11.3), each effect and trigger as its rule (`damage` → 8.1, Death → 9.3), and a summoned token as its own card. A section number stands for all of its rules, in a rule's text ("(7)", "(11.3)") as for a keyword. A test checks that every reference resolves, and the mapping's switches have no default, so an unmapped keyword, effect or trigger does not compile. When the eval shows a missing context (11.3.4 says "protected" without citing 11.3.1), the fix is a reference added to the rule's text, not a retrieval heuristic. No parent-section or neighbor-rule expansion: neighbors are often unrelated and add noise.
- **Pipeline**:

  ```
  question ─┬→ card names found     → their card chunks
            ├→ BM25                 → list A ┐
            └→ embedding → cosine   → list B ┘→ RRF (k = 60) → top ~20
  named cards + top ~20 → + refs (one level, deduplicated) → cross-encoder reranker → top 10, with their refs → prompt
  ```

  RRF fuses the two lists by rank (Σ 1 / (60 + rank)), since a BM25 score and a cosine cannot be compared. The cards a question names are found by their exact names, as whole words, whatever their case, and join the top ~20, so that their refs are followed too; a misspelt name is not caught yet. The refs are attached **before** the reranker, so a chunk relevant through its reference is not dropped. The prompt holds 10 chunks: a chunk is about 40 tokens, so 10 cost about 400, and no question of the dataset needs more than 10 rules (5 would cap 8% of them).
- **Storage**: Postgres + pgvector, the RAG's only source of chunks (the engine's `chunks.jsonl` is only the handoff the index reads), two tables: one row per chunk (its text and refs), and one row per chunk and embedding model (its vector), so several models' vectors sit side by side and are compared without re-embedding; with 191 chunks, an exact scan needs no vector index. BM25 runs in memory (Postgres full-text search is not BM25). Re-indexing recomputes the embeddings of the chunks whose hash changed, and the index records the rulebook's commit. When the FastAPI service arrives (§5.5), indexing becomes one of its endpoints and the handoff file goes: the engine posts its chunks once its batch is done, and the service saves them, answers `202 Accepted` at once and embeds the changed ones **asynchronously**, in the background, since embedding takes about a minute on CPU (longer on a model's first download) and the engine must not wait on it.
- **Prompt**: instructions (answer only from the context, cite the IDs, say so when the context does not hold the answer), then the context in tags carrying the IDs (cards first, then rules in the reranker's order, each followed by its refs), then the question in its own tag. The prompt is **the same for every configuration** of the matrix (`base` gets it without the context), and it stays **generic**: a hint aimed at one interaction ("watch rule 11.3.6") would tune the Arbiter to that interaction's test questions.
- **Output**: one schema for every question, `{answer, citations, answerable}`. `answer` is the natural-language text the user reads, with the citations shown as links: nobody sees JSON. `citations` lists the rule and card IDs in the order they apply. `answerable` lets the Arbiter abstain. Structured outputs through the API for `api+rag`, constrained decoding for local models, validated with Pydantic (one retry; format failures are measured).
- **Abstention**: the `answerable` field is the baseline; a threshold on the best reranker score (no model call) and Jev *noul* are variants (experiment 5, §6.4).
- **Prompt injection**: the Arbiter has no tools, no secrets and only our own sources, so an injection can only make it say something wrong to its own user. Tags, the output schema, the router's `off_topic` class and a few adversarial questions in the dataset are enough; no guard model.

**LoRA versions.**

**Principle: LoRA learns the mechanics and how to arbitrate, not the cards.** Card content arrives through the context (RAG), so changing a card does not break the adapter. LoRA-knowledge is the deliberate counter-example, there to show why memorizing cards is a bad idea.

What fine-tuning should teach:

- always cite the rules;
- break the resolution down step by step, following the order in which effects apply;
- end with a clear verdict, in the format above;
- sound like an official referee.

Three adapters, trained on the **same base model**, with the **same technique** and the **same hyperparameters**. Only the training data changes:

| Adapter | Training data | What it learns | Analogy |
|---|---|---|---|
| **LoRA-knowledge** | Question → answer, no context | Memorize rules and cards in its weights | The student who learns the book by heart |
| **LoRA-RAFT** | Question + retrieved passages (the right one + distractors) → answer citing the right passage | Use the context and ignore noise | The student who practices open-book exams with fake pages slipped in |
| **LoRA-format** | Answers in the strict format (citations, steps, verdict, tone) | The form; facts come from RAG | The student who learns how to write the answer sheet |

What the matrix reveals: if `lora-format+rag` does almost as well as `lora-raft+rag`, the gain from fine-tuning comes mostly from the format, not from understanding. That would be a real finding.

Each adapter is trained **3 times** (seeds 1, 2, 3), see §6.3.

### 5.3 AI opponents

Two families of bots, plugged into the same player protocol (§3.4):

- **algorithmic, in Java**: `random`, `greedy`, `mcts`. MCTS is the **online demo's opponent**: strong, fast and free;
- **model-based, in Python**: `llm-only`, `jev-only`, `jev+llm`. Because of their cost, they stay local and in tournaments.

**System 1 / System 2** architecture of the model-based bots, connected to the Java engine through gRPC:

- **System 2 (API LLM)**: at the start of a turn, analyzes the game state and produces a short **plan** ("I'm behind, I need to stabilize the board and keep my board-clear spell").
- **System 1 (Jev)**: for each decision, the engine sends the **legal actions**; Jev receives the state + the plan and picks through a *choice* question. Probabilities and confidence are logged.
- **Escalation**: if Jev's confidence is below a threshold, the LLM is called again for that specific decision.

Key advantage: the engine only offers legal moves, so there are **zero illegal moves**, unlike an LLM playing in free text.

Additional uses of Jev during a game:

- *score* questions: "how favorable is this position?" → advantage curve shown live in the UI.
- *noul* questions: "should I mulligan?", "should I intercept this attack?".

**Jev's scope**: it only concerns the AI opponent and a few peripheral pieces (§5.4). The Arbiter, RAG, LoRA and the judge do not depend on it.

**Watch out**: Jev is a very recent proprietary model (launched mid-September 2026). It sits behind a `DecisionModel` interface with a fallback implementation (LLM with structured output, or a small local classifier), so the project stays usable without an API key.

### 5.4 Jev elsewhere in the project (peripheral, all replaceable)

- **Unified chatbot router**: *choice* question → `coach` / `arbiter-rag` / `arbiter-lora` / `arbiter-rag+lora` / `off_topic`. Replaceable by an LLM with structured output.
- **MCP guardrail (optional)**: *noul* "was this tool call explicitly requested?", on top of the static annotation (§5.1).
- **RAG relevance filter**: *noul* "does this chunk help answer the question?" → compared with a classic reranker (experiment 5, §6.4).

### 5.5 Unified chatbot

A single entry point that routes to the right module. It is the final demo, **run locally** (API costs). The Angular frontend talks to the Spring backend, which handles authentication and forwards to the Python AI service.

---

## 6. Evaluation protocol

This is the most important part of the repository for a recruiter.

### 6.1 Datasets

**Questions are generated, answer keys are computed.** Nobody writes an answer key the engine can compute. A subset is **checked by hand** (≥ 150 annotated questions).

| Category | Example | Target volume | Answer key from |
|---|---|---|---|
| Factual | "How much does Ash Warden cost?" | 200 | The card JSON, through templates on its fields |
| Simple rule | "What does Overcharge do?" | 150 | The rule: one question per rule of sections 1 to 11, written once (by Luna 6 for v1); the key facts are the rule's own sentences |
| Interaction | "What happens if a unit Linked to an Anchored unit is hit by Tempest?" | 200 | The engine's scenario service |
| Multi-step | A sequence of 3 actions with chained Echoes | 100 | The engine's scenario service |
| Unanswerable | A question about a mechanic that does not exist | 100 | Abstention: invented names absent from the catalog and the rulebook, open points of the rulebook; written once |
| Deck building | "Is this deck legal?" | 50 | The engine's `DeckValidator`, on valid decks and mutated ones (a 31st card, one copy too many, a card of another faction) |

The category is a tag used to balance the dataset and to report scores per category. Neither the Arbiter nor the grading sees it.

**Recipes.** Interaction and multi-step questions come from recipes: Java code, one per interaction, built on the scenario service. A recipe:

1. picks the cards that fit by filtering their data (`keywords` contains `anchor`, `effects` contains `destroy`), so a new card that fits gets its scenarios without new code;
2. builds one board per combination of cards and variants (protected or not, who attacks…), from the scenario service's default board plus only what the interaction needs;
3. names the moves, and the choices asked during resolution (an echo's target);
4. says who asks the question: the question names no card hidden from that player (unless it is played during the scenario), and the key facts are written from their point of view;
5. says which events count;
6. names the **context rules**: the rules that explain the situation rather than what happens, which no event carries. A unit is protected because it arrived on its controller's last turn (11.3.1); its protection is over (11.3.6). A correct answer cites them too.

The engine plays each scenario, and the events the recipe selected give the answer key:

- **key facts**: the sentences `EventDescriber` writes for those events ("Root Sentinel is anchored: it is not destroyed."), redacted as the asker would see them in a game: a draw names no card, since another player's is hidden and the asker cannot know the order of their own deck;
- **essential rules**: the rules those events carry (`rules`), plus the recipe's context rules (`context_rules`, kept in their own field since a recipe wrote them, and checked against the rulebook). The rest of the trace (`CardPlayed[6.3]`, `TurnStarted`…) is incidental.

```json
{
  "category": "interaction",
  "interaction": "anchor+destroy",
  "scenario": "anchor+destroy/protected/root-sentinel/crystal-rupture",
  "question": "My opponent just played Root Sentinel. If I cast Crystal Rupture on it, what happens?",
  "key_facts": ["Root Sentinel is anchored: it is not destroyed."],
  "rules": ["11.3.2", "11.3.3"],
  "context_rules": ["11.3.1"],
  "trace": ["CardPlayed[6.3]", "AnchorPrevented[11.3.2, 11.3.3]", "SpellResolved[6.3, 3.5]"],
  "split": "test"
}
```

**Legality questions.** "Can I play its next step this turn?" is answered from the legal actions of the decision the game waits for once the scenario's choices are played: the engine decides, the recipe writes the sentence for each outcome ("You cannot play the next step of Moonpull this turn."), and the rule that decides it is a context rule, since the engine lists legal actions without saying why the others are not.

**Questions written once.** The simple-rule and unanswerable questions are not computed: they were written once for v1 and are kept as data next to the recipes, then checked at every build. A simple-rule question keeps the text of its rule, and a rule changed since stops the build, since its key facts would be out of date. An unanswerable question holds only while the sources say nothing about it: an invented name that a card or the rulebook now uses, or an open point now settled, stops the build. The questions on open points are tagged `"source": "open-point"`, so the explorer can filter them.

**Final values.** Some questions ask about a value no event states, such as a unit's defense once a bonus ends. A scenario then adds facts read from its final state: the engine computes the value, the recipe words it ("Shardling has 3 defense, out of 3."). A "what happens" scenario must also play its outcome to the end: one that stops on a decision nobody answered (an echo order, a target) would cut its answer key short, so the build refuses it.

**Choices that change the outcome.** By default the question states the choice ("…and its echo targets my Ash Warden"). Some questions are about the choice itself ("Who picks the echo's target?"). A few are answered "it depends": the generator runs the game to the decision, then plays each option from the paused state. The events before the decision give the common key facts; those after it give conditional ones ("If your opponent targets you, you take 3 damage."). Branches with the same outcome are merged, and only one decision branches per question.

**Wording.** Every question starts as a template. Luna 6 (`gpt-6-luna`) rewrites it the way a player would ask; code checks the rewrite (every card name, keyword and number kept, no card or rule number added), then a second call compares it with the template (same situation, same question). A rewrite that fails keeps its template, and so does one a review rejects (`kept-templates.json`); the manifest gives every reason, and the template stays in the entry (`template_question`). A deck question's list of cards never reaches the model. The model comes from a family no evaluated configuration uses (§3.3), so the wording favors none of them. It only takes temperature 1, so reproducibility does not rest on it: responses are cached on disk, and **nothing is worded twice**. A new version keeps the previous version's wording of every question whose template has not changed, so the same question reads the same in every version, and only new questions go to the model. About 1,400 short calls for v1: a few cents.

**Where it runs.** Java (`engine/dataset`): every question and its answer key, deterministic and without a model, exported as JSON. Python (`ai/dataset`): the wording and the split. `scripts/build-dataset.sh` runs both. *(proposed)* Coverage: games between bots, searched for interactions no recipe covers, tell which recipe to write next, and a check fails when a rule of sections 1 to 11 is essential in no test question.

**Splits.** No leakage into the published numbers:

- **test**: one set, shared by every configuration. Nothing is tuned or trained on it; it only produces the published numbers.
- **dev** (~10%): the RAG's settings (thresholds, k, the prompt) are tuned on it, reading its failures.
- **train** (phase 4): the LoRA training data.
- The split is **by interaction**: an interaction and all its scenarios sit on one side, because a fix aimed at an interaction helps all its scenarios. Factual questions are grouped by card, simple-rule questions by rule. Rules are shared by every split: every question uses the core rules.
- **Cards are held out between train and test** as well: a LoRA that saw Root Sentinel in 50 training scenarios would answer from memory instead of from the card text RAG provides. The catalog is small (2 Anchor cards, 2 Destroy cards), so some interactions get one card per side.
- Drawn at random with a fixed seed, stratified by category, and written into the dataset, never recomputed: a group keeps its side from one version to the next, and only new groups are drawn.
- A version has two final datasets, `dataset-dev.jsonl` and `dataset-test.jsonl`, so a run reads one side only.

**Versions.** Questions are frozen per dataset version, in `evals/datasets/<version>/`. A rules or card patch makes a new version: the engine recomputes the answer keys, the previous version is its base for the wording and the split, and every run is redone on it. Each result records its dataset version.

### 6.2 Arbiter configuration matrix

All configurations except the last one use **the same open base model** (§4.2). Each LoRA configuration is evaluated on its 3 adapters (one per seed).

| Config | Description |
|---|---|
| `base` | Base model, no context |
| `base+rag` | Base model + RAG |
| `lora-knowledge` | LoRA-knowledge, no context |
| `lora-knowledge+rag` | LoRA-knowledge + RAG |
| `lora-raft+rag` | LoRA-RAFT + RAG |
| `lora-format+rag` | LoRA-format + RAG |
| `api+rag` | API model (e.g. Haiku) + the same RAG — reference ceiling |

**RAG variants.** The repository exists to compare approaches, so every brick of the RAG pipeline (§5.2) has a baseline and variants:

| Brick | Baseline | Variants |
|---|---|---|
| Chunk prefix | heading path, from a parser | contextual retrieval: once, at indexing, an LLM writes one or two sentences that situate each chunk in its document |
| Retrieval | hybrid, fused by RRF | BM25 only, embeddings only |
| Embedding model | Qwen3-Embedding-0.6B, on CPU | other local models, each one entry of a registry |
| BM25 | Lucene formula, English stemming, stop words kept, k1 1.5, b 0.75 | no stemming, stop words removed, the Robertson, ATIRE, BM25L and BM25+ formulas |
| Expansion | written cross-references | none; before the reranker only |
| Card names | exact, whole words, whatever the case | tolerant of typos (once questions with typos can measure it) |
| Reranker | cross-encoder | none, Jev *noul* |
| Abstention | the `answerable` field | reranker threshold, Jev *noul* |
| Prompt size | 10 chunks after the reranker | 5, 20 |
| Output | API structured outputs / constrained decoding | prompt only |

The whole grid is computed once and stored, retrieval and generation alike, for every configuration. `api+rag` included: any comparison then uses the same RAG on both sides. A run is stored with its configuration, the commit, the index and dataset versions, its metrics and the answer to every question, for the benchmark explorer (§8.2).

### 6.3 What we measure, and how

**Principle: whatever the engine can check, it checks. The judge only grades form.** The engine remains the single source of truth: it writes the key facts and the rules of every answer key. The Arbiter's answers are only read against them.

| Part of the Arbiter's answer | How it is checked |
|---|---|
| Outcome | **Key facts**: for each key fact, an extractor answers a closed question: does `answer` state it, contradict it, or not mention it? A conditional fact counts only if its condition is stated too |
| Rules cited | **Automatic**: recall on the essential rules, the key events' rules and the context rules. A cited rule found elsewhere in the trace is tolerated; a rule that is neither is an error |
| Step order | **Automatic**: the order of the key events' rules in `citations` against the trace |
| Abstention | **Automatic**: `answerable` against the answer key |
| Format compliance | **Automatic**: schema validation |
| Tone, clarity | **Judge** (LLM-as-judge) |

An answer is correct when it states every key fact and contradicts none; the share of key facts covered, and of branches covered for "it depends" questions, is published too. Outcome and rules are scored apart: the right outcome with the wrong rules is a guess, the right rules with the wrong outcome is a reasoning error.

**The extractor** only answers closed questions, one per key fact, with the same generic prompt for every question. It comes from a family no evaluated configuration uses, and its error rate is measured on the hand-checked questions.

**The judge**: a large model called through an API, not trained, with an explicit rubric. It is **calibrated** against human annotations (judge / human agreement published) and chosen from a **different model family** than the `api+rag` model, to avoid self-preference bias.

**Other metrics**:

- Retrieval: **Recall@k** is the decision metric, chosen beforehand: a chunk that is not retrieved cannot be used. **MRR**, precision@k and nDCG are published to explain it. What the reranker receives, the fused top 20 with the named cards and the refs, is longer than any cutoff: the **candidate recall** (the share of the relevant chunks among every candidate) measures it, next to the **number of candidates**, without which returning the whole corpus would score 100%. Retrievers are compared at the same number of chunks by the **recall curve**, Recall@k for every k, rather than by a score weighing recall against size. The relevant chunks of a question are its `citations`, what its answer key says a correct answer cites: the essential rules of a scenario, a deck or a simple-rule question, the card of a factual question. Not every card of a scenario: some only make it playable (a card in a deck so that a draw has something to draw), and the cards a question names reach the prompt by name anyway (§5.2). Unanswerable questions have none and are left out. A random retriever gives the floor every retriever must beat. No weighted composite score: the metrics are correlated, and arbitrary weights could change the winner.
- **Faithfulness** to the context.
- **Correct refusal rate** on unanswerable questions / **hallucination rate**.
- **Latency** p50 / p95 and **cost** per request.

**Statistical rigor**:

- **Tune on dev, publish on test.** A setting picked on the test set (a threshold, a prompt) makes its score optimistic. The explorer shows every run on test; a configuration put forward as recommended is chosen on dev, then its test score is published.
- **3 seeds per adapter.** Training involves randomness: two identical runs with different seeds can differ by a few points. Without several seeds, there is no way to tell whether a 2-point gap comes from the method or from luck. We publish the mean and the spread.
- **Confidence intervals** by bootstrap, resampling **interactions** rather than questions: the questions of one interaction tend to succeed or fail together, so resampling questions would give intervals too narrow. With 100 to 200 questions per category, they span several points.
- **Paired tests** (McNemar): all configurations answer the same questions.
- **Temperature 0** at inference.

### 6.4 Headline experiments

Reading criterion: **do we already know the answer?** Experiments with a predictable outcome serve as baselines or visuals; those with an open outcome are the project's real contributions.

| Experiment | Outcome | Main interest |
|---|---|---|
| 1. Balance patch | Largely predictable | Very visual, perfect for the README |
| 2. Robustness to distractors | Open | A new result |
| 3. Model size | Open | Size vs technique trade-off |
| 4. Small model vs API model | Open | The question companies ask |
| 5. Reranker vs Jev | Open | Cost and latency comparison |

Research has already shown that fine-tuning is poor at injecting new facts, much worse than RAG. `lora-knowledge` without RAG will very likely confirm this: it is a useful baseline, not a discovery.

#### 1. The balance patch

After the LoRA adapters are trained, a patch is published that changes 10 cards and 2 rules, then everything is re-evaluated. **Nothing is retrained**: the point is to measure what happens to adapters trained *before* the patch.

| Component | What happens at the patch | Cost |
|---|---|---|
| Engine + JSON | Cards and rules are changed | It is the patch itself |
| RAG | Changed chunks are re-indexed (RAG involves no training) | A few minutes |
| LoRA | **Nothing, on purpose** | None |
| Test questions | The patched engine recomputes the answer keys | Automatic |
| Evaluation | Every configuration (3 seeds each) is re-run on the affected questions, then graded | Inference only |

Hypotheses:

- **`lora-knowledge`** without RAG answers with the old values, frozen in its weights.
- **`base+rag`** adapts immediately.
- **`lora-knowledge+rag`** is the most interesting case: the old value is in the weights, the new one in the context. Which wins? This is a knowledge conflict, hard to predict.
- **`lora-raft+rag`** should follow the context, since it was trained to.
- **The 2 changed rules**: the format and RAFT adapters saw hundreds of examples applying the old rules, and may have absorbed part of them into their weights. Do they follow the new rule in the context, or the old one?

The experiment is run once or twice, not at every change to the game. For regular patches during the game's life, only RAG is updated.

Lesson for the README: a LoRA that memorizes the cards must be retrained at every patch, which is unsustainable in a company. A LoRA that learns the method, with RAG providing the facts, adapts without retraining.

#### 2. Robustness to distractors

Misleading chunks are deliberately injected into the context. Hypothesis: LoRA-RAFT resists better. RAFT was tested on real documents; the question is whether it holds on invented rules with multi-step interactions.

#### 3. Model size

The same protocol on 1.5B / 3B / 8B. Question: does a small LoRA+RAG model catch up with a large RAG-only model?

Where training happens does not change the result: a 3B trained locally or on a rented GPU gives the same adapter. The variable is the model size; the rented GPU only provides more VRAM for the 8B. It must also be kept for inference on the test set, or the model must be quantized to run locally.

#### 4. Small model vs API model

`lora-raft+rag` (small local model) against `api+rag`. What percentage of the quality, for what cost and what latency? Target conclusion: "X% of the quality, Y times cheaper, Z times slower". This is the question companies ask: can a small model hosted in-house replace a paid API?

#### 5. Reranker vs Jev

Filter chunks with a classic reranker vs with Jev (*noul*). Compare recall, latency, cost.

The same comparison for abstention: the Arbiter's `answerable` field vs a threshold on the reranker's best score vs Jev *noul* ("do these chunks answer the question?", asked before the model call). Measured on the unanswerable questions: does it abstain when it should, and only then?

Results are published as tables + charts in `evals/reports/`, summarized in the README and displayed on the site (§8).

### 6.5 Evaluating the AI opponents

Round-robin tournament between bots, on several decks, with fixed seeds. Orchestrated by the Python harness, played by the Java engine.

| Bot | Language | Description |
|---|---|---|
| `random` | Java | Random legal move (floor) |
| `greedy` | Java | Heuristic: maximizes immediate board value |
| `mcts` | Java | Monte Carlo Tree Search with determinization (reference baseline, online demo opponent) |
| `llm-only` | Python | An LLM that picks among the legal moves, at every decision |
| `jev-only` | Python | Jev alone, without a plan |
| `jev+llm` | Python | Full System 1 / System 2 architecture |

**MCTS and hidden information.**

There are two levels of simulation, not to be confused:

1. the **tournament**: N real games between two bots;
2. **inside each MCTS decision**: hundreds of imaginary games, played with the engine, to compare the possible moves.

To simulate the rest of the game ("I play X, the opponent answers Y, I draw Z"), MCTS needs the opponent's hand and the deck order. The trap: since it runs inside the engine, the easiest path is to clone the internal state, which contains the real opposing hand and the real deck order. The bot then simulates with the hidden cards and **cheats** without anyone intending it. Consequences: its Elo is inflated, every comparison with it is skewed, and it knows the human player's hand in the frontend.

**Countermeasure: determinization.** Before simulating, hidden information is replaced with a plausible guess, then the process is repeated with many different guesses (ISMCTS, *Information Set MCTS*). Since a player only knows the opposing faction (§2.1):

- **known** cards in the opposing hand stay fixed (e.g. a Fracture that returned to hand, with its step);
- the rest of the opposing hand is drawn at random from the cards of their faction and the neutral cards (respecting the 2-copy limit);
- the bot's own deck is shuffled.

**Never clone the raw internal state** in MCTS: it only goes through the engine API's determinization function (§3.4).

**Thinking budget**: MCTS gets a budget per decision (e.g. 500 simulations or 300 ms). Against a human, one second of thinking is perfectly fine.

Metrics: **Elo**, win rate per matchup, **latency per decision**, **cost per game**, number of escalations to the LLM.
The real question the README answers: **how much performance do we keep while dividing cost and latency?**

Budget: games involving model-based bots are the slowest and most expensive. The number of games per matchup is fixed upfront (and justified by a confidence interval on the win rate), and these tournaments run as manual or nightly jobs.

Bonus (optional): distill the `mcts` bot's decisions into a small LoRA model (behavior cloning) and add it to the ranking.

---

## 7. Local training and evaluation

LoRA training and the evaluation matrix run **locally**. It is occasional work, but not one-off:

- several runs are expected (failed first attempts, hyperparameter tuning);
- 3 seeds per adapter, so 3 times the training time: reasonable at 1.5B–3B;
- the patch experiment (§6.4) requires re-running the **evaluation** (not the training) after the docs change.

Indicative VRAM for QLoRA:

| Model size | Recommended VRAM |
|---|---|
| 1.5B – 3B | 8 to 12 GB |
| 8B | ~24 GB |

If the local GPU is limited, stay at 1.5B or 3B: plenty for the demonstration. For the size experiment (8B), or as a last resort, a GPU rented by the second (e.g. RunPod, RTX 4090 around $0.34/h on Community Cloud, September 2026 price to be checked) costs a few dollars per run, training **and** evaluation inference included. Always shut pods down after use.

CI has no GPU: the CI "smoke" eval runs with the RAG Arbiter on an API model, or with a small quantized model on CPU.

---

## 8. Online demo and local demo

### 8.1 Principle: zero AI cost online

No GPU and no call to a model API from the public site. Recruiters see the project and its results without running anything; those who want to try the Arbiter themselves run it locally.

Reminder: most recruiters will not click on the demo. The README GIF, the results tables and the code quality matter more.

### 8.2 The site

| Content | Hosting |
|---|---|
| Precomputed evaluation results (matrix, patch, Elo, costs) | Static (GitHub Pages, Cloudflare Pages…) |
| **Benchmark explorer**: set every parameter (configuration, each RAG variant of §6.2, dataset version) and see the matching results, by category | Static (precomputed runs) |
| **Answer comparator**: pick a question and see the answers of the configurations side by side, with the engine's answer key | Static (precomputed answers) |
| Games against the `random` / `greedy` / `mcts` bots (optional) | Java backend on Cloud Run CPU, scale to zero |

The explorer is the heart of the site: the repository exists to compare approaches, and it lets a visitor do the comparing. It only reads precomputed runs, so the whole grid of §6.2 is computed beforehand, and every question can be opened in the comparator. Each evaluation is one JSON file, `evals/reports/<version>/<split>/<timestamp>.json`, written once and never rewritten: its runs are named by their layers (`{"retriever": "bm25", "answer": null}`), so the explorer switches between them by layer. It says why it was run, and each run what its layers do, as each retriever describes itself from its settings ("RRF (k = 60) of BM25 (…) and dense retrieval (…)"); the evaluations written before these descriptions have none. Each question keeps the rank of each of its relevant chunks in the whole list, so the comparator shows where a cited chunk landed past the 20 chunks it keeps. There is no index to maintain: the game server lists a split's timestamps (`GET /api/evaluations/{version}/{split}`) and serves each file as written (`…/{timestamp}`). Both sides store them behind a contract (a repository in Python, a DAO in the server), a folder today, read at each request so a new evaluation shows at once, a bucket once online. The comparator shows the Arbiter at work without running anything: far more telling than a table.

Cloud Run can run several instances of the game server and does not guarantee that two messages of a game reach the same one. So no instance keeps a game in memory between two messages: **games live in a small shared store**, and a pub/sub carries each update to the instance holding the player's connection (see `specs/phase-2-engine.md` §13.4). Phase 2 runs on in-memory adapters; the store is chosen when going online. Options: a serverless Postgres with a free tier (LISTEN/NOTIFY for the pub/sub, pgvector later), Firestore (serverless, built-in listeners) or Redis. A managed Cloud SQL instance stays on permanently and is billed monthly.

Protection, even without AI: per-visitor rate limiting, a cap on concurrent games and a bounded MCTS thinking budget in demo mode, a budget alert on the GCP project.

### 8.3 Local demo (`docker compose --profile demo up`)

- **A GPU-free path is mandatory**: LoRA Arbiter through a small quantized model (Ollama or llama.cpp), or RAG Arbiter through an API with a key provided by the user.
- Coach and unified chatbot available with an API key.
- Evaluation results **already computed and published** in `evals/reports/`.
- **Test the setup on a clean machine.** A recruiter who takes the time to run the project and hits an error is worse than no demo at all.

### 8.4 Later option (outside the roadmap): LoRA Arbiter online

If the LoRA Arbiter ever has to be queryable online:

- **Cloud Run with an L4 GPU** (24 GB), billed by the second, scale to zero. Ballpark: ~$0.67/h in a Tier 1 region (September 2026, to be checked), plus at least 4 vCPU and 16 GB of memory billed on top. Always `min-instances = 0`, `max-instances = 1`, budget alert. Long cold start (vLLM loading the model): small quantized model, weights baked into the image, a "the Arbiter is waking up…" message in the UI.
- **A provider that hosts the LoRA adapter** and bills per token. Terms vary between providers: check when choosing.
- In every case: rate limits and spending caps on API accounts.

---

## 9. Agentic development workflow

Documented in `docs/dev-workflow.md`, with screenshots and real examples.

- `CLAUDE.md` / `AGENTS.md` at the root, plus one per sub-project (`engine/`, `ai/`, `frontend/`) with each ecosystem's commands and conventions.
- **Project knowledge as skills** in `.claude/skills/`: how the rules work, how cards are written, how the architecture fits together. Any contributor using Claude Code gets the same context.
- **Spec-driven**: each feature starts with a spec in `specs/`, implemented by a coding agent, reviewed by a human.
- **PR review agent** in GitHub Actions.
- **Per-ecosystem CI**, triggered by changed paths:
  - Content (in place): card and deck schemas, card and deck rules, text templates (`content/tests/`) on every push or PR touching `content/` (`.github/workflows/content.yml`);
  - Java: Maven build, engine JUnit tests (rules, interactions, **one test per ruling**), backend integration tests;
  - Python: lint, pytest, RAG "smoke" eval (≈ 50 questions, no GPU) on every PR touching `ai/rag/`, `docs/` or `content/` → PR blocked if accuracy drops by more than X points;
  - Proto: gRPC contract compatibility check (`buf breaking`);
  - Frontend: Angular build and tests;
  - Site deployment (static + Cloud Run CPU) on the main branch;
  - Bot tournament as a manual job; full LoRA matrix run locally.
- **Assisted content generation**: one agent proposes new cards, a second agent checks balance through `simulate_matches` (MCTS bots), a human validates.

---

## 10. Repository layout

```
shardbound/
├── CLAUDE.md
├── README.md
├── docker-compose.yml          # profiles: dev, demo (no GPU)
├── .claude/
│   └── skills/                 # project knowledge for Claude Code
├── specs/                      # feature specs (agentic workflow)
├── content/
│   ├── pyproject.toml          # small uv project that runs the content tests
│   ├── cards/                  # card.schema.json, text-templates.json, README.md + <faction>/<slug>.json
│   ├── decks/                  # deck.schema.json, README.md + <id>.json (starter decks)
│   └── tests/                  # card and deck schemas, card and deck rules, text templates
├── proto/                      # shared protobuf / gRPC contracts
├── engine/                     # Maven multi-module (groupId fr.daliush.shardbound), see specs/phase-2-engine.md
│   ├── pom.xml
│   ├── core/                   # the domain, no Spring: rules, legal actions, events + rule trace, views, scenarios, determinization, bots
│   ├── api/                    # Spring Boot: REST (cards, decks, games) + WebSocket game server; later MCP and gRPC
│   ├── dataset/                # evaluation scenarios: recipes played by core, exported with their answer keys (§6.1)
│   ├── corpus/                 # the Arbiter's chunks: rules, vocabulary terms and cards with their refs (§5.2)
│   └── (repository/)           # later, with Postgres
├── ai/                         # Python (uv)
│   ├── dataset/                # the evaluation dataset's wording and split (§6.1)
│   ├── chat/                   # orchestrator, coach, Arbiter, router, MCP client
│   ├── rag/                    # ingestion, retrieval, reranking
│   ├── lora/                   # dataset generation, training (3 seeds), configs
│   ├── serving/                # local serving (vLLM) + CPU demo profile (Ollama / llama.cpp)
│   ├── bots/                   # LLM, Jev, Jev+LLM bots (gRPC clients)
│   ├── decision/               # DecisionModel interface (Jev + fallback)
│   └── service/                # FastAPI service exposed to the backend
├── frontend/                   # Angular: test frontend, then polished frontend, results pages, comparator
├── deploy/                     # static site + Cloud Run CPU (limits, budget)
├── scripts/                    # build-dataset.sh: a dataset version from end to end
├── docs/
│   ├── design.md               # this document
│   ├── rules/                  # rulebook: README.md index + one file per section (00-vocabulary.md … 12-open-points.md)
│   ├── rulings/                # rulings = docs + JUnit tests + eval questions
│   ├── patches/
│   └── dev-workflow.md
├── evals/
│   ├── datasets/               # <version>/: the engine's questions, then dataset-dev.jsonl and dataset-test.jsonl
│   ├── harness/
│   └── reports/                # <version>/<split>/<timestamp>.json: one file per evaluation, published
└── .github/workflows/
```

---

## 11. Roadmap

Solid foundations first, then AI, then the polished app. Each phase produces something presentable on its own; **from the end of phase 4, the portfolio is publishable.**

| Phase | Content | Demo deliverable |
|---|---|---|
| **0. Foundations** | Monorepo, `CLAUDE.md`, skills, Maven multi-module, `uv` project, Docker, per-ecosystem CI | Clean repo, agentic workflow in place |
| **1. Rules and content** | Settled rules (rulebook), first rulings, JSON Schema + first 40 cards (target 80) | Complete, unambiguous rules |
| **2. The engine** | Java engine, API for its four uses (§3.4): player protocol, scenarios + rule trace, cloning + determinization. Rulings as JUnit tests, `random` / `greedy` bots, small test frontend | Game playable in a minimal frontend, fully tested engine |
| **3. The RAG Arbiter** | Hybrid pipeline, dataset generator plugged into the scenario service, eval harness, calibrated judge | RAG Arbiter + first numbers |
| **4. The LoRA Arbiter** | Training datasets, 3 adapters × 3 seeds (locally), full matrix, patch experiment, comparison with `api+rag` | Comparison table RAG vs LoRA vs API model |
| **5. The coach** | Minimal Spring backend (collection, decks), Java MCP server, Python orchestrator, coach on an API model | GIF "build me a deck and simulate" |
| **6. AI opponents** | `mcts` with determinization, gRPC contract, `llm-only` / Jev / `jev+llm` bots, Elo tournament | Elo ranking + cost / latency |
| **7. App and unification** | Polished Angular frontend, deck builder, router, guardrails, evals in CI, end-to-end traces | Complete local demo in 2 minutes |
| **8. Going online** | Static site (results + comparator), games against MCTS on Cloud Run CPU, `demo` profile tested on a clean machine, final README | Site online with zero AI cost |

---

## 12. Risks and mitigations

| Risk | Mitigation |
|---|---|
| The game grows too big | Cap at 80 cards and 5 keywords for v1. The game is a pretext. |
| Ambiguous rules → noise in the evaluation | Rules settled before the engine (rulebook), engine = specification, one JUnit test per ruling. |
| Engine API to be rewritten later | Designed from the start for its four uses (§3.4), not for the frontend. |
| Two ecosystems to maintain (Java + Python) | Clear boundaries, a single protobuf contract, path-triggered CI, one `CLAUDE.md` per sub-project. |
| Data models drifting between languages | JSON Schema for cards, protobuf for the rest, code generation on both sides. |
| RAG / LoRA comparison skewed by different models | Same open base model for the whole matrix; the API model only appears as a reference. |
| Gaps caused by training randomness | 3 seeds per adapter, bootstrap confidence intervals, paired tests. |
| Judge-biased evals (LLM-as-judge) | The engine writes the key facts and the rules; citations and steps are checked by code; an extractor only answers closed questions about key facts; the judge only grades form, is calibrated against human annotations and comes from a different family than the API model. |
| Wording or grading that favors one configuration | The paraphraser and the extractor come from a family no evaluated configuration uses; the prompt is the same for every configuration. |
| Tuning on the test set | Settings are tuned on dev; test only produces published numbers; the prompt stays generic, with no hint aimed at an interaction. |
| MCTS cheating with hidden information | Determinization; never clone the raw internal state. |
| Cost and slowness of simulations with model-based bots | Algorithmic bots by default, model-based tournaments capped and run offline. |
| Not enough local VRAM | Stay at 1.5B–3B; a GPU rented by the second as a last resort. |
| Online costs | No AI online: static site + Cloud Run CPU scale to zero, budget alert. |
| Local setup failing for a recruiter | GPU-free path, tested on a clean machine. |
| Dependency on Jev (recent API, access and pricing may change) | Scope limited to the AI opponent and peripheral pieces; `DecisionModel` interface + local fallback; the project runs without a key. |
| Confusing Arbiter and judge in the docs | Glossary (§0); "Arbiter" for the chatbot, "judge" only for evaluation. |
| Train / dev / test leakage | Split by interaction everywhere, cards also held out between train and test, the split written into the dataset; checked by a script. |
| Scope creep | Follow the roadmap: finish a phase before starting the next. |

---

## 13. Showcase README (checklist)

- [ ] 20-second GIF at the top: a game against MCTS + the coach chat.
- [ ] Link to the online site (results, answer comparator, games against MCTS).
- [ ] Architecture diagram with the Java / Python / local GPU boundary clearly visible.
- [ ] "Why this language for each component" section (from §3.1).
- [ ] "RAG vs LoRA: who needs the weights" section (from §4.1 and the §4.3 table).
- [ ] "How the evaluation is checked" section: ground truth computed by the engine, rule trace, limited role of the judge.
- [ ] Arbiter matrix results table (3 seeds, confidence intervals), including against the API model.
- [ ] "Before / after patch" chart, with the lesson: LoRA learns the method, not the cards.
- [ ] Bot Elo ranking with cost and latency.
- [ ] "Why no AI online" section (deliberate choice, costs, local demo).
- [ ] "What I learned" section (trade-offs, failures, surprises).
- [ ] "Enterprise transposition" section: the same architecture to plug AI assistants into an existing Java information system.
- [ ] `docker compose --profile demo up` (no GPU) + one command to run the evals.
