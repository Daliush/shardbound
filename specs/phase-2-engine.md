# Phase 2 — Engine, game server and test frontend

> **Status**: approved design, ready for implementation (2026-10-06).
> **Audience**: the Claude Code session that implements phase 2. Everything decided with the maintainer is written here; you should not need the conversation that produced it.
> **Scope**: `engine/core` (the rules engine), `engine/api` (REST + WebSocket game server), `frontend/` (minimal Angular client), the `random` and `greedy` bots, determinization and the scenario service.

---

## 0. Read this first

### 0.1 Reading order

1. `CLAUDE.md` (language, commit convention, working agreements).
2. This spec, entirely.
3. The rulebook: `docs/rules/README.md` and every file it links. **The rulebook is the source of truth for game rules.** This spec decides *how* they are implemented; it never overrides them. If the two disagree, stop and ask the maintainer.
4. `content/cards/README.md`, `content/cards/card.schema.json`, `content/cards/text-templates.json`, `content/decks/README.md`, and a few card files.
5. `docs/design.md` §3 (architecture) and §6.5 (bots, determinization).

### 0.2 Working agreements (from `CLAUDE.md`, repeated because they matter)

- **Game and product decisions belong to the maintainer.** When something is not decided here or in the rulebook, do not invent it silently: ask, with numbered questions and a recommendation. If you must fill a gap to keep going, write the rule in the rulebook marked *(proposed)*, list it in `docs/rules/12-open-points.md`, and tell the maintainer.
- The questions of section 15 were asked and answered on 2026-10-05; the answers are in the rulebook and in section 8. Rules still marked *(proposed)* are listed in `docs/rules/12-open-points.md`: confirm them before implementing the affected behavior.
- Repository content is in **English**. Talk to the maintainer in the language of their message (usually French).
- **One branch and one pull request per slice** (section 17). Commits follow the convention in `CLAUDE.md` (Conventional Commits, imperative, body explains why, `Co-Authored-By` trailer for Claude). Never force-push, never rewrite pushed history.
- `gh` is not installed on the maintainer's machine: push the branch and give the maintainer the compare link to open the PR, or ask them to install `gh`.
- Keep docs in sync at the end of each slice: `docs/design.md` changelog, `CLAUDE.md` (commands, status), `.claude/skills/shardbound-project/SKILL.md` (status).
- Downloading build dependencies (Maven Wrapper and Maven Central, npm registry) is authorized for this work.

### 0.3 Definition of "done" for any slice

- All tests green locally and in CI.
- Every rule implemented has at least one test whose display name starts with its rule ID (section 16).
- Docs in sync (see above). PR opened with the template of section 17.

---

## 1. Scope

**In scope**

- `engine/core`: content loading, the full rulebook (sections 1–11), events with rule IDs, player views, decisions and legal actions, deterministic RNG, card text rendering, random and greedy bots, determinization, scenario builder and runner.
- `engine/api`: REST resources (cards, decks, bots, games), the WebSocket game protocol, in-memory game sessions, human vs bot and human vs human (join code).
- `frontend/`: a minimal, plain-looking Angular client to play a game.
- CI for `engine/` and `frontend/`.

**Out of scope (do not build)**

MCTS, gRPC and Python clients, any database or `repository` module, user accounts and authentication, timers for human vs human games, replay endpoint, deployment (a local `docker compose up` runs the game), polished UI, animations, deck builder, collection, boosters, MCP server, anything AI.

---

## 2. Decisions already taken (do not reopen)

| Topic | Decision | Why |
|---|---|---|
| Layout | `engine/` Maven multi-module with `core/` and `api/`; `frontend/` at the root. A `repository` module comes later with Postgres. | The maintainer wants a domain layer and a Spring layer. Separate Maven modules make "core has no Spring" a compile-time guarantee. |
| Java | Java 21 (installed locally, LTS) | Records, sealed interfaces, pattern matching, virtual threads. |
| Build | Maven with the Maven Wrapper (`mvnw`) committed | Anyone, CI included, builds without installing Maven. |
| Packages | `fr.daliush.shardbound` (groupId `fr.daliush.shardbound`) | Maintainer's choice. Fine since nothing is published to Maven Central. |
| Core dependencies | Jackson only (to read content JSON), plus test libraries | Core must load cards without Spring (tests, simulator, bots). |
| State | Immutable game state; `apply(state, action)` returns a new state and events | Free copies for MCTS and scenarios, trivial tests, safe concurrency, determinism. |
| Engine output | Both the new state and the list of events | State alone loses "how"; events alone would force clients to reimplement rules. |
| Hidden information | The full `GameState` never leaves the server; clients only get their `PlayerView` and redacted events | Prevents cheating through the browser. |
| Actions | Fully specified (targets included) and listed by the engine; a player answers with the index of the chosen action | One protocol for humans and bots; the client never builds actions. |
| Mid-resolution choices | Pending decisions in the state (intercept, echo targets, discard choices…), possibly for the non-active player | Same mechanism everywhere; works over the network. |
| Real choices only | The engine asks a player only when there are at least 2 options (except the main-phase decision, always asked) | Fewer network round trips; no pointless prompts. |
| Transport | REST for resources (cards, decks, bots, creating/joining games); raw WebSocket with a typed JSON protocol for live games | Turn-based game with server push; one channel for the game. |
| Messages | Every server message carries the full player view; events are deltas; the full history is sent on (re)connection | The client renders the view, animates/logs events, reconnects trivially. |
| Identity | A random seat token per game and seat, no accounts | Reconnection to the same seat without accounts; accounts come later. |
| First version | Human vs bot, but the server supports two humans from day one (join code) | Lets the maintainer test intercepts with two browsers. |
| Determinism | Seed + decks + actions reproduce a game exactly | Debugging, replays, evaluations. |
| Several instances | No instance owns a game: sessions behind a port with versioned saves, notifications through another port; in-memory DAOs in phase 2, a shared store at deployment (section 13.4) | Cloud Run can run several instances and does not guarantee that a game's messages reach the same one. |

---

## 3. Repository layout and build

```
engine/
├── mvnw, mvnw.cmd, .mvn/wrapper/       Maven Wrapper (committed)
├── pom.xml                             parent: Java 21, dependency management, plugin config, modules
├── core/
│   ├── pom.xml                         depends on Jackson only; Maven Enforcer bans org.springframework*
│   └── src/main/java/fr/daliush/shardbound/core/
│       ├── content/    card and deck definitions, catalog, loader, deck validation
│       ├── state/      GameState, PlayerState, Unit, Relic, HandCard, ids, modifiers, resolution
│       ├── action/     Action, TargetRef
│       ├── decision/   Decision, DecisionKind
│       ├── event/      GameEvent (sealed), Visibility, redaction
│       ├── view/       PlayerView
│       ├── rules/      GameEngine and the rule implementations
│       ├── text/       CardTextRenderer, EventDescriber, ActionDescriber
│       ├── random/     SplitMix64
│       ├── bot/        Player, RandomBot, GreedyBot, Evaluator, Determinizer
│       └── scenario/   ScenarioBuilder, ScenarioRunner
└── api/
    ├── pom.xml                         Spring Boot (web, websocket, validation, test)
    └── src/main/java/fr/daliush/shardbound/api/      controller → domain ← adapter → dao (checked by ArchitectureTest)
        ├── controller/ the endpoints: rest/ (controllers, dto/), ws/ (handshake, handler, GameSocketRegistry, message/), mappers/
        ├── domain/     no transport, no JSON: bo/ (game, view, command, error), services/ (game, update, bot, security,
        │               content), mappers/view, ports/ (GameSessionPort, GameNotificationPort, GameWatcher)
        ├── adapter/    the ports implemented on the DAOs: game/, notification/, mappers/ (entity ↔ business object)
        ├── dao/        pure data: entities/, game/ (GameDao), notification/ (GameNotificationDao), each with *InMemory
        └── config/     content and engine, clock and settings, WebSocket, the shardbound.* properties
frontend/                               Angular 21 workspace (section 14)
.github/workflows/engine.yml            CI for engine/ (section 3.5)
.github/workflows/frontend.yml          CI for frontend/ (section 3.5)
```

### 3.1 Maven

- Parent `engine/pom.xml`: `groupId fr.daliush.shardbound`, artifacts `shardbound-engine` (parent, `pom`), `shardbound-core`, `shardbound-api`. `maven.compiler.release=21`, UTF-8.
- Use the latest stable **Spring Boot** release available at implementation time (check start.spring.io or Maven Central; do not guess a version). Import its BOM (`spring-boot-dependencies`) in the parent's `dependencyManagement` so that `core` and `api` share one Jackson version. Importing a BOM manages versions only; it adds no Spring dependency to `core`. Use the Jackson major version and packages that this Spring Boot release manages.
- `core` dependencies: Jackson databind (+ annotations if needed); tests: JUnit 5, AssertJ.
- `api` dependencies: `spring-boot-starter-web`, `spring-boot-starter-websocket`, `spring-boot-starter-validation`, `spring-boot-starter-test`, and `shardbound-core`. `spring-boot-maven-plugin` only in `api`.
- **Maven Enforcer** in `core`: a `bannedDependencies` rule excluding `org.springframework*` (transitive included), so the boundary cannot erode.

### 3.2 Commands (document them in `CLAUDE.md`)

```bash
cd engine && ./mvnw verify                      # build and test everything
cd engine && ./mvnw -pl api -am spring-boot:run # run the server on http://localhost:8080 (builds core in the reactor)
cd frontend && npm start                        # run the client on http://localhost:4200 (proxies /api and /ws)
cd frontend && npm test                         # client unit tests
docker compose up --build                       # both, from the root: client on http://localhost:4200
```

### 3.3 Locating the content

- `core` loads content with `ContentLoader.load(Path contentDir)`, where `contentDir` is the repository's `content/` folder (it contains `cards/` and `decks/`).
- Tests find it by walking up from the working directory until they find `content/cards/card.schema.json`. Put that lookup in a small test utility.
- `api` reads the property `shardbound.content-dir`. When it is not set, use the same upward lookup. Fail at startup with a clear message if the folder is not found.

### 3.4 Configuration properties (`api`)

| Property | Default | Meaning |
|---|---|---|
| `shardbound.content-dir` | auto-detected | Path to the repository's `content/` folder |
| `shardbound.sessions.finished-ttl` | `30m` | How long a finished game is kept |
| `shardbound.sessions.idle-ttl` | `2h` | How long a game without any action is kept |
| `shardbound.sessions.eviction-interval` | `1m` | How often the in-memory DAO drops expired games |
| `shardbound.bots.step-delay` | `0ms` | Optional pause between two bot actions (the client paces them already) |
| `shardbound.websocket.allowed-origins` | `http://localhost:*`, `http://127.0.0.1:*`, `http://[::1]:*` | Origin patterns a browser may open a game's WebSocket from |

### 3.5 CI

- `.github/workflows/engine.yml`: on push to `main` and pull requests touching `engine/**`, `content/**` (the engine reads it) or the workflow itself; `actions/setup-java` with Temurin 21 and Maven cache; `cd engine && ./mvnw -B verify`.
- `.github/workflows/frontend.yml`: on push to `main` and pull requests touching `frontend/**` or the workflow; `actions/setup-node` (Node 24, npm cache); `npm ci`, `npm run build`, unit tests headless and non-watching.
- The existing `content.yml` stays as is.

---

## 4. Core: content model

Immutable records mirroring `card.schema.json` and `deck.schema.json`. Enum JSON values are snake_case. Parse strictly: unknown fields, unknown enum values and missing required fields are errors. The content tests already validate the files, but the engine must not trust them blindly.

```java
record CardId(String value) {}             // "ember.ash-warden"
record DeckId(String value) {}             // "ember-starter"
enum Faction { EMBER, TIDE, ROOT, NEUTRAL }
enum CardType { UNIT, SPELL, RELIC }
enum Keyword { ANCHOR, OVERCHARGE }
enum Trigger { ARRIVAL, DEATH, DEPARTURE, TURN_START, TURN_END, CONTINUOUS, ATTACK }   // Cast (9.1) is implicit for spells

sealed interface CardDefinition permits UnitCard, SpellCard, RelicCard {
    CardId id(); String name(); Faction faction(); Set<Keyword> keywords(); Optional<String> flavor();
}
record UnitCard(CardId id, String name, Faction faction, OptionalInt cost /* empty for tokens */, boolean token,
                int defense, Set<Keyword> keywords, int sacrificeCost, List<AttackDef> attacks,
                List<AbilityDef> abilities, Optional<String> flavor) implements CardDefinition {}
record SpellCard(CardId id, String name, Faction faction, OptionalInt cost /* empty for Fracture */, Set<Keyword> keywords,
                 int sacrificeCost, List<Effect> effects, List<FractureStep> fracture, Optional<String> flavor) implements CardDefinition {}
record RelicCard(CardId id, String name, Faction faction, int cost, Set<Keyword> keywords, int sacrificeCost,
                 List<AbilityDef> abilities, Optional<String> flavor) implements CardDefinition {}

record AttackDef(Optional<String> name, int cost, List<Effect> effects, OptionalInt echo) {}
record AbilityDef(Trigger trigger, List<Effect> effects) {}
record FractureStep(int cost, List<Effect> effects) {}

enum TargetSpec { ALLY_UNIT, ENEMY_UNIT, ANY_UNIT, RANDOM_ALLY_UNIT, RANDOM_ENEMY_UNIT, SELF, ATTACK_TARGET,
                  ALL_ALLY_UNITS, ALL_ENEMY_UNITS, ALL_UNITS, YOU, OPPONENT, ANY_PLAYER,
                  ALLY_RELIC, ENEMY_RELIC, ANY_RELIC }

sealed interface Effect {}
record Damage(int amount, TargetSpec target) implements Effect {}                                   // 8.1
record Destroy(TargetSpec target) implements Effect {}                                              // 8.2
record Sacrifice(int count) implements Effect {}                                                    // 8.3 (default count 1)
record Heal(int amount, TargetSpec target) implements Effect {}                                     // 8.4
record Modify(int attackDamage, int defense, Duration duration, TargetSpec target) implements Effect {}  // 8.5
record Draw(int amount, TargetSpec target) implements Effect {}                                     // 8.6
record Discard(int amount, TargetSpec target, DiscardChoice choice) implements Effect {}            // 8.7 (PLAYER, RANDOM)
record ReturnToHand(TargetSpec target) implements Effect {}                                         // 8.8
record Summon(CardId token, int count) implements Effect {}                                         // 8.9 (default count 1)
record Freeze(TargetSpec target) implements Effect {}                                               // 8.10
record Link() implements Effect {}                                                                  // 8.11
record GainShards(GainMode mode, int amount) implements Effect {}                                   // 8.12 (THIS_TURN amount, MAX → 1)
record Recall() implements Effect {}                                                                // 8.13
record StatAura(TargetSpec target, int attackDamage, int defense) implements Effect {}              // 8.14
record CostAura(Side player, CardTypeFilter cardType, int change) implements Effect {}              // 8.14 (Side YOU/OPPONENT)
enum Duration { PERMANENT, END_OF_TURN }

record DeckEntry(CardId card, int count) {}
record Deck(DeckId id, String name, Optional<String> description, Faction faction, List<DeckEntry> cards) {}
record CardCatalog(Map<CardId, CardDefinition> cards) {}
record Content(CardCatalog catalog, Map<DeckId, Deck> decks) {}
```

- `ContentLoader.load(contentDir)` reads `cards/*/*.json` and `decks/*.json` (skipping `*.schema.json`). It also checks cross-references: every deck card exists, every `summon.token` is a token.
- `DeckValidator.problems(deck, catalog)` ports the content tests' rule checker (rules 2.1–2.4, same messages with rule IDs). `GameEngine.newGame` refuses an illegal deck.

---

## 5. Core: game state

All records are immutable. Lists are unmodifiable copies. Internally, `apply` may use a private mutable working copy and freeze it at the end: immutability is an API guarantee, not an implementation constraint.

```java
enum PlayerId { P1, P2 }                   // seats; who starts is decided by the RNG (5.1.1)
record InstanceId(int value) {}             // unique per card instance in a game, deterministic (counter)

record GameState(
    int turn,                               // global turn number, 1 = first turn after mulligans; 0 during setup
    PlayerId active,                        // the player whose turn it is
    PlayerId firstPlayer,
    PlayerState p1, PlayerState p2,
    Resolution resolution,                  // pending work (section 7)
    Optional<PendingDecision> pending,      // who must choose, and what
    long rng,                               // SplitMix64 state
    int nextInstanceId, int nextArrivalSeq, int decisionSeq,
    Optional<GameResult> result) {}

record PlayerState(
    PlayerId id, Faction faction, DeckId deckId,
    int hp,                                 // 50 at start, max 50 (1.1)
    int maxShards, int shards, int lockedNextTurn,   // 4.x, 11.4
    int fatigue,                            // number of empty-deck draws so far (1.4)
    int turnsTaken,
    boolean mulliganDecided,
    List<CardInstance> deck,                // ordered top first; hidden
    List<HandCard> hand,
    List<Unit> units,                       // board units, in arrival order
    List<Relic> relics,
    List<CardInstance> graveyard) {}        // oldest first; public

record CardInstance(InstanceId id, CardId card, PlayerId owner) {}
record HandCard(CardInstance card, int fractureStep /* 0-based next step */, int lastFractureTurn /* 0 = never */) {}

record Unit(
    InstanceId id, CardId card, PlayerId owner, PlayerId controller, boolean token,
    int arrivalSeq, int arrivedTurn,
    int defense, int maxDefense,            // current and max, both already include modifiers and auras
    List<Modifier> modifiers,
    Map<InstanceId, AuraBonus> appliedAuras, // aura contributions currently applied (8.14)
    boolean hasAttackedThisTurn, boolean hasInterceptedThisTurn,
    int frozenThroughTurn,                  // 0 = not frozen; frozen while state.turn <= this (8.10)
    boolean anchorProtected, boolean doomed, // 11.3
    Optional<InstanceId> linkedTo) {}       // 11.5

record Modifier(int attackDamage, int defense, Duration duration, InstanceId source) {}
record AuraBonus(int attackDamage, int defense) {}
record Relic(InstanceId id, CardId card, PlayerId owner, PlayerId controller, int arrivalSeq) {}

sealed interface GameResult permits Win, Draw {}
record Win(PlayerId winner, EndReason reason) implements GameResult {}     // reason HP
record Draw(EndReason reason) implements GameResult {}                     // DOUBLE_KO (1.3), TURN_LIMIT (1.5)
```

- **Owner and controller** are always equal in v1 (no effect steals control), but keep both: the rules use both words.
- **Arrival sequence**: a global counter assigned when a unit or relic arrives on the board. It orders simultaneous triggers (9.8) and the canonical order of targets and actions. A card that leaves the board keeps its sequence for the triggers it causes (9.8).
- **Effective attack damage** of a damage effect inside one of the unit's attacks = printed amount + Σ `modifiers.attackDamage` + Σ `appliedAuras.attackDamage`, floored at 0 (8.5).
- `PendingDecision` stores the decision kind, the deciding player, the decision id and whatever is needed to resume (section 7).

---

## 6. Core: engine API

```java
public final class GameEngine {
    public GameEngine(CardCatalog catalog);

    /** Validates the decks, sets up the game (5.1) and runs until the first decision. */
    public Transition newGame(GameSetup setup);

    /** The decision the game is waiting for; empty once the game is over. */
    public Optional<Decision> decision(GameState state);

    /** Applies one of the decision's actions and runs until the next decision or the end of the game.
        Throws IllegalActionException if the action is not one of decision(state).actions(). */
    public Transition apply(GameState state, Action action);

    /** What `viewer` is allowed to see. `history` is the game's event log so far (the view redacts it). */
    public PlayerView view(GameState state, PlayerId viewer, List<GameEvent> history);

    /** The same events as `viewer` is allowed to see them. */
    public List<GameEvent> eventsFor(List<GameEvent> events, PlayerId viewer);
}

record GameSetup(Deck p1Deck, Deck p2Deck, long seed) {}
record Transition(GameState state, List<GameEvent> events) {}
record Decision(String id, PlayerId player, DecisionKind kind, List<Action> actions) {}   // id = "d-" + decisionSeq
enum DecisionKind { MULLIGAN, MAIN, INTERCEPT, CHOOSE_TARGET, CHOOSE_CARDS, CHOOSE_ORDER }
```

One `apply` call can produce many events: ending a turn runs the end of your turn and the start of the opponent's turn until their main-phase decision.

### 6.1 Actions

```java
sealed interface Action {}
record KeepHand() implements Action {}
record Mulligan() implements Action {}
record PlayCard(InstanceId card, boolean overcharge, List<TargetRef> targets, List<InstanceId> sacrificed) implements Action {}
record Attack(InstanceId attacker, int attackIndex, Optional<TargetRef> target) implements Action {}
record Intercept(InstanceId interceptor) implements Action {}
record DeclineIntercept() implements Action {}
record ChooseTarget(TargetRef target) implements Action {}
record ChooseCards(List<InstanceId> cards) implements Action {}     // discard choice, sacrifice-effect choice
record ChooseOrder(List<Integer> order) implements Action {}        // two echoes of the same card (11.1.8)
record EndTurn() implements Action {}

sealed interface TargetRef {}
record UnitTarget(InstanceId id) implements TargetRef {}
record RelicTarget(InstanceId id) implements TargetRef {}
record PlayerTarget(PlayerId player) implements TargetRef {}
record GraveyardCardTarget(InstanceId id) implements TargetRef {}
```

- `PlayCard.targets` holds one entry per **choice slot** of the card, in printed effect order. A choice slot is an effect whose target is chosen by the player: `ALLY_UNIT`, `ENEMY_UNIT`, `ANY_UNIT`, `ANY_PLAYER`, `ALLY_RELIC`, `ENEMY_RELIC`, `ANY_RELIC` (one slot each), `Link` (two slots: two different units), `Recall` (one slot: a unit card in the controller's graveyard). For a Fracture spell, the slots are those of the step being played.
- A slot with **no valid option** is skipped (no entry), and the card stays playable: the effect will do nothing (10.3).
- Which cards to **discard**, and which units to **sacrifice through a Sacrifice effect**, are chosen at resolution (`CHOOSE_CARDS`), because the hand and board can change during resolution.
- `PlayCard.sacrificed` lists the units paid as a sacrifice cost (6.3, 8.3), exactly `sacrificeCost` of the controller's own units.
- Actions are records: value equality is what `apply` uses to validate them.

### 6.2 Canonical order of legal actions

The order must be deterministic: bots are seeded, and clients answer with an index.

- **MULLIGAN**: `KeepHand`, then `Mulligan`.
- **MAIN**: every `PlayCard` (cards in hand order; for one card, without overcharge before with overcharge; then target combinations in canonical target order; then sacrifice combinations in canonical order), then every `Attack` (attackers in arrival order; attack index; targets in canonical order; no target last), then `EndTurn`.
- **INTERCEPT**: `DeclineIntercept`, then `Intercept` by interceptor arrival order.
- **CHOOSE_***: options in canonical order.
- **Canonical target order**: units by arrival sequence, then relics by arrival sequence, then players (the deciding player first), then graveyard cards from oldest to newest. Combinations are ordered lexicographically on that order. A Link pair is listed once, as (lower arrival sequence, higher arrival sequence).

### 6.3 Player view

```java
record PlayerView(
    PlayerId viewer, int turn, PlayerId active, boolean yourTurn,
    SelfState self, OpponentState opponent,
    Optional<Decision> decision,                    // present only when the viewer must decide
    Optional<PendingSummary> waitingFor,            // when the opponent must decide: who and which kind (no actions)
    Optional<GameResult> result,
    Deck ownDeck,                                   // a player knows their own decklist
    List<GameEvent> history) {}                     // the event log, already redacted for the viewer
```

- `SelfState`: faction, hp, max hp, shards, max shards, locked next turn, fatigue, deck count, hand (cards with their Fracture step), units, relics, graveyard, mulligan decided.
- `OpponentState`: faction, hp, max hp, shards, max shards, locked next turn, fatigue, deck count, **hand count only**, units, relics, graveyard. The opponent's Fracture steps are not shown (11.2.5); they can be deduced from `history`.
- Deck order is never in a view. The **seed is never exposed** while a game is running: it would reveal every deck order.

### 6.4 Events

```java
sealed interface GameEvent {
    List<String> rules();          // rule IDs applied, e.g. ["8.1", "11.5.2"]; never empty
    Visibility visibility();       // PUBLIC or PRIVATE_TO(owner)
    GameEvent redacted();          // the form other players see (identity for public events)
}
```

The events are the **rule trace**: they are the answer key for the Arbiter's citations and step order, and the game log. Minimum set (add more when needed; every event carries its rule IDs):

| Event | Rules | Visibility |
|---|---|---|
| `GameStarted(firstPlayer)` | 5.1.1 | public |
| `CardDrawn(player, card)` | 5.1.2 / 5.2.3 / 8.6 | private to the drawer; redacted form hides `card` |
| `HandKept(player)`, `MulliganTaken(player)` | 5.1.3 | public |
| `TurnStarted(player, turn)` | 5.2 | public |
| `AnchorProtectionEnded(unit)` | 5.2.1, 11.3.1 | public |
| `ShardsRefilled(player, max, available, locked)` | 4.1, 4.2, 11.4.2 | public |
| `HpLost(player, amount, reason)` | 1.4 (fatigue) | public |
| `CardDiscarded(player, card, reason)` | 3.3 (overdraw), 8.7, 11.2.6 | public (the graveyard is public) |
| `CardPlayed(player, card, cost, overcharged, fractureStep)` | 6.3, 11.4.1, 11.2.2 | public |
| `UnitSacrificed(unit)`, `SacrificeFailed(player, needed, available)` | 8.3, 8.22 | public |
| `UnitArrived(unit)`, `RelicArrived(relic)` | 6.3, 6.5 | public |
| `TokenSummoned(unit)`, `SummonFailed(player)` | 8.9, 3.4 | public |
| `AttackDeclared(attacker, attackIndex, target)` | 7.4 | public |
| `AttackIntercepted(originalTarget, interceptor)`, `InterceptDeclined(defender)` | 7.5 | public |
| `AttackCancelled(attacker)` | 7.10 | public |
| `UnitDamaged(unit, amount)`, `PlayerDamaged(player, amount)`, `DamageShared(from, to, amount)` (amount can be 0, 8.15) | 8.1, 11.5.2 | public |
| `UnitHealed(unit, amount)`, `PlayerHealed(player, amount)` | 8.4 | public |
| `Modified(unit, attackDamage, defense, duration)`, `ModifierExpired(unit, …)` | 8.5, 5.4.2 | public |
| `Frozen(unit, throughTurn)`, `UnitThawed(unit)` | 8.10 | public |
| `Linked(a, b)`, `LinkBroken(a, b)` | 8.11, 11.5.4 | public |
| `ShardsGained(player, amount, mode)` | 8.12 | public |
| `ReturnedToHand(card)`, `SentToGraveyardHandFull(card)` | 8.8 | public |
| `Recalled(card)` | 8.13 | public |
| `UnitDestroyed(unit, cause)`, `RelicDestroyed(relic)` | 6.6, 8.2, 5.4.3 | public |
| `TokenVanished(unit)` | 3.6 | public |
| `AnchorPrevented(unit, what)` | 11.3.2, 11.3.3 | public |
| `UnitDoomed(unit)`, `DoomLifted(unit)` | 11.3.4 | public |
| `AbilityTriggered(source, trigger)` | 9.x | public |
| `EchoTriggered(unit, attackIndex, percent)` | 11.1.1, 11.1.2 | public |
| `FractureAdvanced(card, nextStep)`, `SpellResolved(card)` | 11.2.2, 11.2.4 | public |
| `AuraApplied`, `AuraRemoved` | 8.14 | public |
| `TurnEnded(player)` | 5.4 | public |
| `GameEnded(result)` | 1.2, 1.3, 1.5 | public |

Events name the cards they involve (`CardInstance`: instance id, card id, owner), so a log can be read without the state. `core/text/EventDescriber` turns an event into an English sentence from a viewer's point of view ("You draw Spark Dart.", "Your opponent draws a card.", "Sprout #12 takes 3 damage."). The API sends it with each event; it will also feed the LLM work later.

---

## 7. Core: resolution model

`apply` is a small interpreter over the work stored in `GameState.resolution`:

```java
record Resolution(List<Step> steps, List<QueuedTrigger> triggerQueue) {}
```

- **Steps** are the remaining work of what is currently resolving: an action, a turn transition, or one triggered ability. They run front first, and a step can push sub-steps to the front.
- **The trigger queue** is FIFO. Triggered abilities wait there and run only when `steps` is empty: **an action fully resolves before the abilities it triggered** (9.11).
- Both are part of the immutable state, so a game can stop in the middle of a resolution for a decision and resume in a later `apply` call. Steps are records; design them as you see fit (for example `StartTurn`, `EndTurn`, `ResolveEffects(source, controller, effects, nextIndex, chosenTargets, echoPercent, attackTarget)`, `ResolveAttack(attacker, attackIndex, target, phase)`, `FinishSpell(card)`).

```
apply(state, action):
    check action ∈ decision(state).actions, else IllegalActionException
    work = mutable copy of state; events = []
    feed the action: start a new resolution (MAIN, MULLIGAN), or resume the paused step with the choice
    run(work)
    return Transition(freeze(work), events)

run(work):
    loop:
        if work.result is present: stop
        if work.pending is present: stop
        if work.steps is not empty: execute the first step       (it may emit events, push steps, queue triggers, set a decision)
        else if work.triggerQueue is not empty: start the first queued trigger (its effects become steps)
        else: set the idle decision (MAIN for the active player); stop
        after every atomic effect: stateCheck()
```

**Atomic effect**: one effect applied to all of its targets. "Deal 4 damage to all enemy units" damages every unit first, then the state check runs once, so those units die **simultaneously**.

```
stateCheck():
    repeat until nothing changes:
        reconcile auras (8.14)
        for each unit with defense <= 0:
            if anchorProtected: it becomes doomed if it is not already (11.3.4)
            else if already doomed: it stays; 5.4.3 destroys it at the end of the turn its protection ended (11.3.5)
            else: it is destroyed; all such units are destroyed at the same time (6.6)
    collect the triggers caused by this atomic step (arrivals, deaths, departures), order them (9.8, 9.10), append to triggerQueue
    if a player has hp <= 0: end the game now (1.2, 1.3, 1.6)
```

**Decisions during resolution**: when a step needs a choice (a trigger's target, an echo target, a discard, a sacrifice effect, an order):
- 0 options: the effect does nothing (10.3);
- 1 option: chosen automatically, no decision;
- 2 or more: set `pending` (new decision id), keep the step at the front, stop. The next `apply` gets the choice and resumes the step.

**Intercept** is a decision for the defender, asked only if at least one eligible interceptor exists (section 8, rule 7.5).

**Ordering a batch of simultaneous triggers** (9.8, 9.10). Sort key:
1. the source card's controller is the active player (0) or not (1);
2. the source card's arrival sequence (a card that just left the board keeps its own);
3. for the same card: Echo, then Death, then Departure;
4. printed order on the card.

If one card has two Echo attacks, the owner chooses their order (`CHOOSE_ORDER`, 11.1.8). Abilities triggered while the queue is being processed go to the back (9.8).

**Turn transitions are steps.** Some steps must wait for the trigger queue to be empty (for example, after the "Turn end" abilities have triggered, 5.4.2 must wait until they have resolved). Give steps a way to say "drain the trigger queue before running me".

**Randomness**: SplitMix64 stored in `GameState.rng`; every random draw advances it. Used for: who starts (5.1.1), shuffles (Fisher–Yates: P1's deck, then P2's; mulligans), random targets, random discards. Bots have **their own** RNG and never touch the game's. Same seed + same decks + same actions ⇒ identical states and events (tested).

---

## 8. Rules implementation reference

For each rulebook section: how the engine implements it. Rule IDs in parentheses point to the rulebook, including the rules settled with the maintainer for the engine (section 15).

### Section 1 — The game

- **1.1** `hp = 50`; max HP 50.
- **1.2 / 1.3** After every atomic step, if a player has `hp <= 0`, the game ends at once: one player at 0 → `Win(other, HP)`; both → `Draw(DOUBLE_KO)`. Steps and triggers still waiting are dropped (1.6). Emit `GameEnded`.
- **1.4** Each draw from an empty deck: `fatigue += 1`, `hp -= fatigue`. This is HP loss, not damage: Link does not share it. Emit `HpLost(reason FATIGUE)`. It applies to every draw: start of turn and Draw effects.
- **1.5** After the end-of-turn steps of global turn 100 (each player's 50th turn), if no result: `Draw(TURN_LIMIT)`.

### Section 2 — Deck building

- **2.1–2.4** `newGame` validates both decks with `DeckValidator` (same checks and messages as `content/tests/contentlib.py`). Illegal deck → `IllegalArgumentException`.

### Section 3 — Zones

- **3.3** Drawing with 10 cards in hand: the drawn card goes to the graveyard (`CardDiscarded(reason OVERDRAW)`, public).
- **3.4** A unit cannot be played with 6 own units on the board, nor a relic with 3 relics (not a legal action). Summon with a full board: nothing (`SummonFailed`).
- **Board places**: count them through a single function, summing each unit's size. `UnitCard.size()` is always 1 for now (not in the card format yet): the maintainer plans a `size` field, so keep board-limit and sacrifice counting in one place each.
- **3.8** Exception: a unit with a sacrifice cost is playable on a full board when at least one of the units in `PlayCard.sacrificed` is not Anchor-protected (it really leaves). The triggers of the sacrifice wait (9.11), so the freed place is still free when the unit arrives.
- **3.5** The graveyard keeps insertion order. Dead cards, discarded cards and resolved spells go there. Tokens never do.
- **3.6** A token leaving the board vanishes: when it dies, its Death abilities still trigger, then `TokenVanished`. When returned to hand: `TokenVanished` instead of going to the hand.
- **3.7** Views and events are redacted (6.3, 6.4).

### Section 4 — Shards

- **4.1 / 4.2** At the start of the active player's turn: `maxShards = min(10, maxShards + 1)`, `shards = max(0, maxShards - lockedNextTurn)`, `lockedNextTurn = 0`.
- **4.3** Paying a card or an attack subtracts its cost; an action you cannot pay is not legal.
- **4.4** At the end of the turn: `shards = 0`.

### Section 5 — Game sequence

- **5.1.1** First player = `rng.nextBoolean()` (P1 or P2).
- **5.1.2** Shuffle both decks (P1's, then P2's), then each player draws 5 (first player first).
- **5.1.3** `MULLIGAN` decision for the first player, then the second. `Mulligan`: the whole hand goes back into the deck, shuffle, draw 4. Once both have decided, turn 1 starts for the first player.
- **5.2** Start of turn, in order: first, units whose freeze has ended thaw (`UnitThawed`, 8.10); **5.2.1** end of Anchor protection for the active player's units; **5.2.2** Shards (4.1, 4.2); **5.2.3** draw 1 (except the first player's very first turn); **5.2.4** "Turn start" abilities of the active player's units and relics (by arrival order) go to the queue. At the same moment, reset `hasAttackedThisTurn` and `hasInterceptedThisTurn` on every unit: "this turn" in 7.2 and 7.5 means each turn, the opponent's included. When everything has resolved: `MAIN` decision.
- **5.3** `MAIN` lists every legal `PlayCard`, `Attack` and `EndTurn`. It is always asked, even when `EndTurn` is the only option.
- **5.4** `EndTurn`: **5.4.1** "Turn end" abilities of the active player's cards to the queue, then drain it; **5.4.2** remove `END_OF_TURN` modifiers (8.5 for defense); **5.4.3** destroy the active player's doomed units that are no longer protected and still have 0 defense (11.3.5); **5.4.4** `shards = 0`; then 1.5; then start the opponent's turn (`turn += 1`).
- **5.5** The non-active player never gets `MAIN` or `PlayCard`: only `INTERCEPT` and the `CHOOSE_*` decisions that rules give them (5.5.2).

### Section 6 — Cards

- **6.3 Playing a card** (`PlayCard`), in order:
  1. Compute the cost (6.8): printed cost (or the Fracture step cost) + every cost aura − 2 if overcharged, then floor the total at 0 (once, at the end). Pay it.
  2. Pay the sacrifice cost: each unit in `sacrificed` dies (`UnitSacrificed`; Death and Departure triggers are queued). An Anchor-protected unit counts as paid but stays (11.3.3).
  3. Overcharge: `lockedNextTurn += 2` (11.4.2).
  4. Unit or relic: arrives on the board (arrival sequence, `arrivedTurn = turn`, `defense = maxDefense` from the card, `anchorProtected` if it has Anchor), auras reconcile; its Arrival abilities are queued. Spell (9.1 Cast): resolve its effects (or the step's effects) in printed order, using the chosen targets; then **11.2** for Fracture, otherwise the spell goes to the graveyard (`SpellResolved`).
- **6.5** Arrival: `defense = maxDefense`, both including the auras that apply (reconciliation).
- **6.6** Handled by the state check.
- **6.7** Returning to hand resets everything: a new `HandCard` with the same instance id, no modifiers, no damage, no link, no flags.

### Section 7 — Combat

- **7.1** A unit has 1 or 2 attack abilities. An attack "has a target" if one of its effects targets `ATTACK_TARGET`.
- **7.2** An `Attack` is legal if: the attacker belongs to the active player; `arrivedTurn < turn`; it is not frozen (`turn > frozenThroughTurn`); `!hasAttackedThisTurn`; the player can pay that attack's cost. Overcharge never applies to attacks (11.4.5).
- **7.3** Valid targets of an attack with a target: every enemy unit (frozen, anchored and doomed units included). If the enemy has no unit: the enemy player. Relics are never attack targets.
- **7.4 / 9.9** Resolving an `Attack`:
  1. pay the cost, set `hasAttackedThisTurn`, emit `AttackDeclared`;
  2. resolve the attacker's "Attack" abilities **right away** (not through the queue: rule 9.9 places them before the intercept decision; their consequences, such as deaths, still queue normally);
  3. if the target is a unit still on the board and the defender has at least one eligible interceptor: `INTERCEPT` decision for the defender (7.5);
  4. resolve the attack's effects in printed order: `ATTACK_TARGET` is the (possibly redirected) target; `SELF` is the attacker, if still on the board; `YOU` is its controller; `ALL_ALLY_UNITS` are its controller's units; every damage effect adds the attacker's effective damage bonus (8.5, 8.18).
  - If the target left the board before step 3 (for example killed by an "Attack" ability): no intercept, effects on `ATTACK_TARGET` do nothing, the other effects apply, and it still counts as the unit's attack (7.9).
  - If the attacker left the board before step 4 (for example because of its own "Attack" abilities): the attack does not take place, none of its effects apply, the cost stays paid (7.10).
- **7.5** Eligible interceptors: the defender's units other than the target, not frozen, `!hasInterceptedThisTurn` (anchored and doomed units included). Intercepting sets `hasInterceptedThisTurn` and emits `AttackIntercepted`. Spells, abilities and echoes are never intercepted.
- **7.6** No retaliation.
- **7.7** Spell targets are not limited by 7.3.
- **7.8** An attack without a target never asks for an intercept, still counts as the unit's attack and still triggers "Attack" abilities.

### Section 8 — Effects

**Resolving targets** at resolution time, for an effect controlled by C:
- Chosen targets (`ALLY_UNIT`, `ENEMY_UNIT`, `ANY_UNIT`, `ANY_PLAYER`, relic specs): for a spell, from `PlayCard.targets`. For a triggered ability or an echo, all its chosen targets are picked when it starts resolving, in printed order (10.6): one `CHOOSE_TARGET` decision for C per slot that has at least 2 options. A chosen target that is no longer valid when its effect resolves → that effect does nothing (10.3, 10.5).
- `RANDOM_ALLY_UNIT` / `RANDOM_ENEMY_UNIT`: drawn with the game RNG among the valid units at resolution; none → nothing.
- `SELF`: the source unit or relic if it is still on the board; otherwise nothing.
- `ALL_*`: the matching units when the effect starts; each receives it in arrival order; then one state check (simultaneous deaths).
- `YOU` / `OPPONENT`: relative to C. `ATTACK_TARGET`: only inside attacks and echoes.
- Relics can only be targeted by Destroy and Return to hand (10.4).

**Effects:**
- **8.1 Damage**: the amount (printed + bonuses) is floored at 0 (8.15). On a unit: Link first (11.5.2), then `defense = max(0, defense - amount)` (6.9); on a player: `hp -= amount` (HP can go below 0). A 0 amount still hits: `DamageDealt(target, 0)` is emitted and nothing changes (with Link, both shares are emitted, 0 and 0).
- **8.2 Destroy**: a unit dies, unless Anchor-protected (`AnchorPrevented`). A relic goes to the graveyard (`RelicDestroyed`); its Death and Departure abilities trigger.
- **8.3 Sacrifice** (as an effect): never partial (8.16). C chooses `count` of their own units (`CHOOSE_CARDS` when C has more units than `count`; automatic when exactly `count`). No sacrifice, no ability (8.22): when a triggered ability or an echo starts resolving, if its controller cannot make all of its Sacrifice effects, the whole ability does nothing (`SacrificeFailed`); if a Sacrifice effect can no longer be made when it is reached, it and the effects after it are skipped (`SacrificeFailed`). They die (Death triggers). An Anchor-protected unit counts but stays (11.3.3).
  - Legality (8.16): a `PlayCard` is legal only if the controller has at least sacrifice cost + Σ `count` of the spell's Sacrifice effects units on the board; an `Attack` only if they have at least Σ `count` of the Sacrifice effects of that attack ability and of the unit's "Attack" abilities (the attacker counts as one of their units).
- **8.4 Heal**: a unit: `defense = min(maxDefense, defense + amount)`, and if it was doomed and is now above 0, `DoomLifted` (11.3.4). A player: `hp = min(50, hp + amount)`.
- **8.5 Modify**: `+X` goes into the attack damage bonus; `+Y` is added to both `maxDefense` and `defense`. A debuff can bring `defense` to 0, and the state check handles it. Keep a `Modifier` with its duration. When an `END_OF_TURN` modifier expires (5.4.2, at the end of the current turn, whoever's it is, 8.19): remove its attack bonus; if `Y > 0`: `maxDefense -= Y`, `defense = min(defense, maxDefense)`, which never kills; if `Y < 0`: `maxDefense += |Y|` and `defense = min(maxDefense, defense + |Y|)`, the unit gets back what it lost (8.17); a doomed unit brought back above 0 is no longer doomed (`DoomLifted`, 11.3.4).
- **8.6 Draw**: the target player draws `amount` cards, one at a time (3.3 and 1.4 apply to each).
- **8.7 Discard**: `RANDOM` uses the game RNG; `PLAYER` means the discarding player chooses (`CHOOSE_CARDS` for that player, even during the other player's turn, when their hand has more cards than `amount`; otherwise discard everything). A Fracture card loses its progress (11.2.6).
- **8.8 Return to hand**: a unit or relic goes to its owner's hand, reset (6.7), keeping its instance id. A token vanishes (3.6). With a full hand: graveyard (`SentToGraveyardHandFull`); it does not die, so no Death and no Echo, but Departure triggers. Anchor-protected: prevented.
- **8.9 Summon**: create `count` token units for C, until the board is full (3.4): arrival sequence, `arrivedTurn = turn`, Anchor protection if the token has Anchor; their Arrival abilities trigger.
- **8.10 Freeze**: `frozenThroughTurn = max(frozenThroughTurn, turn + 1)`: frozen for the rest of this turn and the whole next turn, whoever's turns they are. At the start of turn `frozenThroughTurn + 1` it thaws (`UnitThawed`). A frozen unit cannot attack or intercept.
- **8.11 Link**: two different units, any side, neither already linked (11.5.1). Fewer than two eligible units → nothing.
- **8.12 Gain Shards**: `THIS_TURN`: `shards += amount`, which can exceed `maxShards` for this turn. `MAX`: `maxShards = min(10, maxShards + 1)` (current Shards unchanged).
- **8.13 Recall**: a unit card from C's graveyard (chosen: a `PlayCard` slot for spells, `CHOOSE_TARGET` for abilities) goes to C's hand. With a full hand, nothing happens and the card stays in the graveyard (8.20).
- **8.14 Auras** (continuous abilities of units and relics on the board):
  - *Stat aura*: every unit in the target group gets `+attackDamage/+defense`. **Reconciliation**, run in every state check: compute for each unit the bonus each active aura should give, compare with `appliedAuras`; a new or larger contribution adds to `maxDefense` and `defense`; a removed or smaller one lowers `maxDefense` and caps `defense` at the new max (like an expiring bonus, 8.5). A defense malus works the other way: a new or larger malus lowers both `maxDefense` and `defense` (it can kill, 6.6), and a removed or smaller one gives both back (8.21, like 8.17). The attack part is just recomputed. A unit arriving under an aura arrives with the bonus (6.5).
  - *Cost aura*: changes the cost of the given card type for the given player while the source is on the board (cost computation, 6.8).

### Section 9 — Triggers

- **9.1 Cast** is the spell's own resolution (6.3).
- **9.2 Arrival**: when a unit or relic arrives (played or summoned).
- **9.3 Death**: destroyed or sacrificed (units, and relics when destroyed). Echo also triggers on death (11.1).
- **9.4 Departure**: whenever it leaves the board (death, return to hand, full-hand return to the graveyard).
- **9.5 / 9.6** Turn start / Turn end of the controller (5.2.4, 5.4.1).
- **9.7 Continuous**: auras (8.14).
- **9.8 / 9.10** Ordering: section 7.
- **9.9 Attack**: section 8, rule 7.4.

### Section 10 — Targets

Section 8, "Resolving targets".

### Section 11 — Keywords

- **11.1 Echo**: when a unit **dies** (destroyed or sacrificed; not returned to hand), each of its attacks with `echo` triggers an echo: its **printed** effects (11.1.9) with every numeric value multiplied by X% and rounded toward 0 (11.1.2, 11.1.10) (damage, heal and draw amounts, Modify values, Summon count, Gain Shards amount); non-numeric effects apply fully. If the attack has a target, the owner chooses a new one with the 7.3 rules (`CHOOSE_TARGET`, automatic if only one option). `SELF` effects do nothing (the unit is dead), `YOU` is the owner. An echo costs nothing, cannot be intercepted, does not trigger "Attack" abilities and does not count as an attack (11.1.4). Order: 9.10; two echoes on one card: `CHOOSE_ORDER` (11.1.8). Chains are natural: echo deaths queue new echoes (11.1.6).
- **11.2 Fracture**: `HandCard.fractureStep` is the next step. A Fracture card is playable only if `lastFractureTurn != turn` (11.2.3); it costs the step's cost. After a step that is not the last, it returns to its owner's hand with `fractureStep + 1` and `lastFractureTurn = turn` (`FractureAdvanced`); with a full hand, it goes to the graveyard and the progress is lost (11.2.7). After the last step: graveyard (`SpellResolved`). `CardPlayed` shows the step publicly; the opponent's view never shows steps.
- **11.3 Anchor**: on arrival, `anchorProtected = true`; it ends at 5.2.1 of its controller's next turn. While protected: destroy, return to hand and sacrifice have no effect on it (`AnchorPrevented`; a sacrifice still counts as paid); damage and debuffs apply; at 0 defense it becomes `doomed` instead of being destroyed. A doomed unit can still attack and intercept; healed above 0 → `DoomLifted`; back to 0 while still protected → doomed again. At 5.4.3 of the turn during which its protection ended, a doomed unit still at 0 is destroyed; "Turn end" abilities resolve before that (11.3.5). After the protection: normal rules, so a unit reaching 0 is destroyed immediately (11.3.6). A unit doomed during its own arrival turn is **not** destroyed at the end of that turn: its protection has not ended yet.
- **11.4 Overcharge**: for cards with the keyword, both `PlayCard(overcharge = false)` and `PlayCard(overcharge = true)` are offered when affordable. Cost: 6.8. Lock: +2 per overcharged card, stacking; refill per 4.2; any excess is lost (11.4.4).
- **11.5 Link**: `linkedTo` on both units. Damage `n` to a linked unit: it takes `ceil(n / 2)`, its partner `floor(n / 2)`; the transferred part is not shared again (11.5.2). Only damage is shared (11.5.3). When either unit leaves the board, both links are cleared (`LinkBroken`, 11.5.4).

---

## 9. Card text rendering (`core/text/CardTextRenderer`)

Renders a card's rules text from its data and `content/cards/text-templates.json` (read its `$comment` first).

- Line order: keywords line (`"Anchor."`, `"Overcharge."` joined, in the card's keyword order), sacrifice cost, attacks (named: `"{name} ({cost} {Shard|Shards}): {effects}"`; unnamed: `"Attack (…): …"`; then `" Echo {x}."`), Fracture header and steps, spell effects, abilities (`"{trigger}: {effects}"`; continuous abilities without a label).
- Effect sentences use the templates. Variant keys: `heal` → `unit` or `player` from the target class; `modify` → duration; `draw` → target; `discard` → `"<target>.<choice>"`; `return_to_hand` → `single` or `group` (`ALL_*` targets are groups); `gain_shards` → mode; aura → `stats`, or `cost.you` / `cost.opponent` with `aura_cards` and `aura_change`.
- `{target}` uses the `targets` phrases; `self` gives `"this unit"` or `"this relic"`; `{token}` is the token's name.
- `{word|words}` picks singular when the closest preceding number is 1. Modify and aura values are always signed (`+2`, `-1`, `+0`). The first letter of each sentence is capitalized.
- **Golden tests**: the two examples in `content/cards/README.md` (Ash Warden and Moonpull) must render exactly as shown there. Add one test per template key.
- Each line comes with its kind: `keywords`, `sacrifice_cost`, `attack`, `fracture` (the header and each step), `effect` (a spell's effects, on one line) or `ability`. A client can then leave out what it already shows another way, such as a unit's attacks (section 14).
- The API returns the rendered lines with each card (`GET /api/cards`).

---

## 10. Bots and determinization

```java
interface Player { Action choose(PlayerView view, Decision decision); }
```

- **RandomBot(seed)**: uniform among `decision.actions()`, with its own RNG.
- **Determinizer** (`core/bot`): from a `PlayerView` (including `ownDeck` and `history`), builds a complete, plausible `GameState` with a given RNG:
  - public information is copied as is;
  - the viewer's own deck = their decklist minus their cards seen elsewhere (hand, board, graveyard), shuffled;
  - the opponent's hand: known cards stay fixed. Known cards are cards that went back to their hand publicly: Fracture steps, returns to hand, recalls; follow them through `history`. The other cards, and the opponent's deck, are drawn from their faction's cards plus neutral cards, without tokens, respecting 2 copies per card counting every copy already seen.
  - Never read the real hidden state: this is what keeps bots honest (design doc §6.5).
- **GreedyBot(seed)**: for each legal action, determinize once (one sample per decision, from the bot's RNG), apply the action, and score the resulting state with `Evaluator` from the bot's point of view; pick the best, ties broken by lowest index.
  - Evaluator (tunable, documented in code): `(myHp − oppHp) + Σ my units (defense + 2 × best attack damage) − Σ enemy units (same) + 3 × (my unit count − enemy unit count) + 0.5 × my hand size`; a win scores +∞, a loss −∞.
  - Mulligan if the hand has no card costing 2 or less.
- Target for slice 5: GreedyBot beats RandomBot in at least 60% of 200 seeded games (100 per side). If it does not, tune the evaluator and report the numbers to the maintainer.

---

## 11. Scenario service (`core/scenario`)

The engine's third consumer (design doc §3.4): build a board, apply actions, get the exact outcome and the rule trace. Rule tests use it from slice 1.

- `ScenarioBuilder`: a fluent API to build any legal `GameState` without playing a game: turn, active player, each player's HP, Shards, locked Shards, fatigue, hand (with Fracture steps), deck (ordered), units (card, defense, modifiers, flags: protected, doomed, frozen, attacked, link), relics, graveyard, RNG seed. It assigns instance ids and arrival sequences, and validates what it builds (board limits, a link must be mutual…).
- `ScenarioRunner.run(GameState start, List<Action or chooser>)` → every intermediate decision, the final state and the **unredacted** events (with rule IDs). Actions can be given directly or chosen by a small matcher (for example "the PlayCard of Spark Dart targeting unit 12"), so tests do not depend on action indexes.
- A JSON scenario format for the Python dataset generator comes later (phase 3). Do not build it now.

---

## 12. API: REST

Base path `/api`. JSON. Errors as RFC 9457 Problem Details (Spring `ProblemDetail`).

| Method | Path | Request | Response |
|---|---|---|---|
| `GET` | `/api/cards` | — | `[{ id, name, faction, type, cost?, defense?, token, keywords, text: [{ kind, text }], flavor? , fracture?: [{ step, cost }] }]` |
| `GET` | `/api/decks` | — | `[{ id, name, description?, faction, cards: [{ card, count }] }]` |
| `GET` | `/api/bots` | — | `["random", "greedy"]` (only the bots implemented so far) |
| `POST` | `/api/games` | `{ "deck": "ember-starter", "opponent": { "type": "bot", "bot": "random", "deck": "root-starter" }, "seed"?: 42 }` | `201 { gameId, playerToken, websocketPath: "/ws/games/{id}" }` |
| `POST` | `/api/games` | `{ "deck": "ember-starter", "opponent": { "type": "human" } }` | `201 { gameId, playerToken, joinCode, websocketPath }` |
| `POST` | `/api/games/{id}/join` | `{ "joinCode": "…", "deck": "root-starter" }` | `200 { gameId, playerToken, websocketPath }` |

- The creator always takes seat P1, and the bot or the joiner seat P2. Who plays first is decided by the RNG (5.1.1).
- A bot game starts at creation. A human vs human game starts when the second player joins; before that, the creator's view has status `waiting_for_opponent`.
- `seed` is optional (random when missing). It is never returned while the game runs.
- `text` is added in slice 3, with `CardTextRenderer` (section 9): one `{ kind, text }` per line, in the renderer's order. Until then the field is absent, rather than an empty list.
- Optional fields are left out of REST responses when absent (`cost` of a token, `joinCode` of a bot game).
- `playerToken` and `joinCode`: 32 random bytes from `SecureRandom`, base64url. Compare tokens in constant time.
- Validation errors (unknown deck, unknown bot, illegal deck): `400`. Unknown game: `404`. Wrong join code, or game already full: `409`.

---

## 13. API: WebSocket game protocol and session server

### 13.1 Connection

- URL: `ws://host/ws/games/{gameId}?token={playerToken}`.
- A `HandshakeInterceptor` resolves the game and the seat from the token. Unknown game → reject the handshake with `404`; bad token → `401`.
- On connection, the server immediately sends a `state` message.
- One connection per seat: a new connection with the same token replaces the old one, which the server closes with code `4409` (`"replaced by a newer connection"`). The old connection may be on another instance: the new one announces itself through the notification port (`announce(gameId, seat, connectionId)`), and whichever instance holds an older connection for that seat closes it.
- Wrap every `WebSocketSession` in Spring's `ConcurrentWebSocketSessionDecorator`: `sendMessage` is not thread-safe.

### 13.2 Messages

Client to server:

```json
{ "type": "act", "requestId": "c-17", "decisionId": "d-57", "action": 2 }
{ "type": "sync" }
```

Server to client:

```json
{ "type": "state",    "view": { … }, "history": [ … ] }
{ "type": "update",   "view": { … }, "events": [ … ] }
{ "type": "rejected", "requestId": "c-17", "reason": "stale_decision", "message": "Decision d-57 is no longer pending." }
```

- `state`: the full view plus the whole history redacted for this seat. Sent on connection and in answer to `sync`.
- `update`: sent to **both** seats after **each** applied action (bot actions included, one `update` per action): the new view and that action's events, redacted per seat.
- `view.version` increases by 1 with every save of the session: every applied action, and the join. If a client receives a version that is not `last + 1`, it sends `sync`.
- A game against a human is created at version 0, waiting for the opponent. The join sets the game up and is saved as version 1, so the creator receives the setup as an `update` (first player, draws, the mulligan decision); the joiner gets a `state` when it connects. A game against a bot is set up at creation, at version 0.
- `rejected` goes only to the sender. Reasons: `not_your_decision`, `stale_decision`, `invalid_action`, `malformed_message`, `game_not_started`, `game_over`.

### 13.3 DTOs (TypeScript notation; the Java DTOs mirror them)

```ts
type Side = "you" | "opponent";

interface GameView {
  gameId: string; version: number;
  status: "waiting_for_opponent" | "in_progress" | "finished";
  turn: number; activePlayer: Side | null; yourTurn: boolean;
  you: SelfView; opponent: OpponentView | null;      // null while waiting for the opponent to join
  decision: DecisionView | null;                     // what you must decide now, or null
  waitingFor: { player: Side; kind: DecisionKind } | null;
  result: null | { outcome: "win" | "loss" | "draw"; reason: "hp" | "double_ko" | "turn_limit" };
}
type DecisionKind = "mulligan" | "main" | "intercept" | "choose_target" | "choose_cards" | "choose_order";

interface PlayerBase {
  faction: string; hp: number; maxHp: number;
  shards: number; maxShards: number; lockedNextTurn: number; fatigue: number;
  deckCount: number; units: UnitView[]; relics: RelicView[]; graveyard: CardRef[];
}
interface SelfView extends PlayerBase { hand: HandCardView[]; mulliganDecided: boolean; }
interface OpponentView extends PlayerBase { handCount: number; }

interface CardRef { id: number; card: string; }                       // instance id + card id
interface HandCardView extends CardRef {
  cost: number;                 // what playing it costs now (6.8: printed or next step cost + auras), without Overcharge
  fractureStep: number | null;  // 1-based next step, null if not Fracture
}
interface RelicView extends CardRef { controller: Side; }
interface UnitView extends CardRef {
  controller: Side; token: boolean;
  defense: number; maxDefense: number;
  attacks: { index: number; name: string | null; cost: number; damage: number | null; hasTarget: boolean; echo: number | null }[];
  arrivedThisTurn: boolean; hasAttackedThisTurn: boolean; hasInterceptedThisTurn: boolean;
  frozen: boolean; anchorProtected: boolean; doomed: boolean; linkedTo: number | null;
  modifiers: { attackDamage: number; defense: number; duration: "permanent" | "end_of_turn" }[];
}

interface DecisionView { id: string; kind: DecisionKind; prompt: string; actions: ActionView[]; }
interface ActionView {
  index: number; label: string;                       // label: English, from core's ActionDescriber
  type: "keep_hand" | "mulligan" | "play" | "attack" | "intercept" | "decline_intercept"
      | "choose_target" | "choose_cards" | "choose_order" | "end_turn";
  card?: number; overcharge?: boolean; targets?: TargetView[]; sacrificed?: number[];
  attacker?: number; attackIndex?: number; interceptor?: number; cards?: number[]; order?: number[];
}
interface TargetView { kind: "unit" | "relic" | "player" | "graveyard_card"; id?: number; player?: Side; }

interface EventView { type: string; rules: string[]; text: string; [field: string]: unknown; }   // fields per event, sides as "you"/"opponent"
```

Settled on 2026-10-06, for slice 2:

- **Events, one to one.** `type` is the engine event's record name in snake_case (`UnitDamaged` → `unit_damaged`, `PlayerDamaged` → `player_damaged`). The other fields are the record's components in camelCase, `rules` excepted, with: players as `"you"` / `"opponent"`; a `CardInstance` as a `CardRef`; an `EventTarget` as `{ kind: "unit", id, card }` or `{ kind: "player", player }`; enums in snake_case; an empty `Optional` as `null`; a `GameResult` as `{ outcome, reason }` seen by the viewer. One generic mapper builds them from the record components, so a new engine event needs no API change. `text` comes from core's `EventDescriber`.
- **`DecisionView.prompt`** comes from core's `DecisionDescriber` (`core/text`), from the deciding player's point of view. An intercept prompt names the attack, read from the paused attack step.
- **`UnitView.attacks[].damage`**: the damage the attack deals to its target, bonuses included (8.5, 8.18); `null` when it deals none (Mend, Call the Grove, Kindle). Core computes it, next to `Costs`, so labels, prompts and views cannot disagree.
- **Optional fields** are written as explicit `null` in WebSocket messages, as typed above.

### 13.4 Session server (`api/domain`, `api/adapter`, `api/dao`)

**No instance owns a game.** The server will run on Cloud Run, which can start several instances and does not guarantee that two messages of a game, or the two players' connections, reach the same instance. So nothing about a game stays in an instance's memory between two messages: sessions are kept through a port, notifications travel through another, and any instance can process any message. Phase 2 runs locally, in a single process, on in-memory DAOs: they are for local runs and tests only, and must never be deployed with more than one instance. The shared store comes with deployment (`docs/design.md` §8.2, roadmap phase 8), as new DAOs, without touching the domain.

- `GameSession`, a business object: game id; status; the seed; two `Seat`s (a human: deck and SHA-256 hash of the seat token; a bot: deck, name and RNG state; an open seat: the join code's hash); the current `GameState`; `version`; the event log (unredacted, in order); the action log; the outbox; last activity time. The adapter maps it to a `GameEntity` (plain values and engine types) that the DAO stores. The state is a snapshot for fast loading; the action log, with the setup, rebuilds it exactly (determinism), for replays and debugging.
- **Layers**: the domain owns its ports (`GameSessionPort`, `GameNotificationPort`); the adapter layer implements them (`GameSessionAdapter`, `GameNotificationAdapter`) and maps entities to business objects; the DAO layer is pure data. Each DAO is an interface, and each implementation adds a suffix: `GameDaoInMemory` now, `GameDaoDatabase` later.
- **Bots are rebuilt at every step** from their name and RNG state, and their new RNG state is saved with the session. Any instance that loads a session whose decision belongs to a bot (because an instance stopped in the middle of a bot turn) resumes the bot loop; the versioned save makes sure only one instance does.
- `GameSessionPort`: `create(session)`, `find(gameId)`, `save(session, expectedVersion)`, an optimistic lock that answers false when the stored version is no longer `expectedVersion` (the DAO throws `VersionConflict`, the adapter translates it). Phase 2: `GameDaoInMemory` keeps each game as a database row would: version and status beside its JSON.
- **Outbox.** Each save also stores, for each human seat, what it may see after that save: the engine's `PlayerView`, the prompt and labels of its own decision (written then, since the describers read the full state), and the save's events redacted for it. The domain turns them into views when they are sent. The outbox keeps the last 50 versions; a connection further behind gets the `state` instead. A notification never carries the message itself: it only says "game X is now at version n", so it stays tiny (a Postgres `NOTIFY` is capped at 8 KB) and a lost notification costs nothing, since the next one, or a `sync`, catches up.
- `GameNotificationPort`: `publish(gameId, version)`, `announce(gameId, seat, connectionId)` (13.1) and `watch(gameId, watcher)`. The `GameWatcher` callback is the domain's; the adapter forwards the DAO's notifications to it. Phase 2: `GameNotificationDaoInMemory`, in-process, which logs a failing listener instead of letting it reach the publisher. Later: the shared store's pub/sub (for example Postgres `LISTEN/NOTIFY`).
- The services: `GameCreationService` (create, join), `GamePlayService` (act and its checks), `BotTurnService` (the bot loop), `SeatAuthenticationService` (the seat a token holds), `SeatUpdateService` (`state`, `since(version)` from the outbox, `watch`, `announce`), and `GameSaver`, the step every change ends with: outbox, versioned save, publish, log.
- `GameSocketRegistry` (controller): this instance's sockets only. It watches every game it holds a socket to, sends every socket the updates it has not received, in order, and closes sockets replaced elsewhere. A socket is registered before its `state` is read, under the socket's lock, so no update falls between the two. A seat without a socket anywhere simply misses messages and will `sync`.
- WebSocket threads never block: they hand each message to a virtual thread. Inside one instance, a per-game lock avoids pointless conflicts between two messages of the same game; correctness across instances comes from the versioned `save`, not from the lock.
- **Processing `act`**, on any instance:
  1. Load the session. If the game is not started or is over: `rejected`.
  2. Recompute the decision from the state. Never trust the client.
  3. If the `decisionId` differs: `stale_decision` (an old click is stale, whoever holds the new decision). If the seat is not the decision's player: `not_your_decision`. If the index is out of range: `invalid_action`.
  4. `engine.apply`, then `version += 1`, append the events, the action and both seats' `update` messages, `save(session, version - 1)`. On `VersionConflict`, another instance moved the game first: reload and start again at step 1 (the request usually ends as `stale_decision`).
  5. `publish(gameId, version)`.
  6. While the next decision belongs to a bot seat and the game is not over: the bot chooses, then back to 4 (optional `step-delay` between iterations). Guard against bugs: at most 10,000 bot steps per human action, then fail loudly.
- Human vs human: the same flow; whichever seat holds the decision acts, mid-turn intercepts and echo choices included, even when the two players are connected to different instances.
- Eviction: finished games after `finished-ttl`, idle games after `idle-ttl`. A scheduled task evicts from the in-memory DAO; a shared store will use its own expiry.
- Logging: one line per applied action (game id, seat, decision kind, action label, version, instance).

---

## 14. Frontend (`frontend/`)

Minimal and plain: correctness first, no animations required.

- Angular 21 (installed), standalone components, signals, `HttpClient`, RxJS `webSocket()`. No UI library and no state library; plain CSS.
- Dev proxy (`proxy.conf.json`) for `/api` and `/ws` (with `ws: true`) to `http://localhost:8080`, so no CORS configuration is needed.
- **Routes**:
  - `/`: choose your deck (`GET /api/decks`) and an opponent: a bot (`GET /api/bots`, opponent deck) or a human; "Create game". For a human opponent, show the invite link `/join/{gameId}?code={joinCode}`.
  - `/join/:gameId`: choose your deck, join, then go to the game.
  - `/games/:gameId`: the game.
- **Token storage**: `localStorage["shardbound.game.<gameId>.token"]`. Opening `/games/:id` without a token: message plus a link home. Every tab of a browser shares it: to play both seats on one machine, use a second browser or a private window.
- **Game screen**:
  - opponent: faction, HP, Shards (available / max, locked), hand count, deck count, fatigue, units, relics, graveyard count;
  - you: the same, plus your hand. Each card shows name, type, cost and the Fracture step (`GET /api/cards`, cached), and its rendered text (slice 3, see below);
  - each unit shows name, defense / max defense, its attacks (name, cost, damage, Echo X) and badges: arrived this turn, attacked, intercepted, frozen, anchored, doomed, linked to #id;
  - **decision panel**: the `prompt` and the decision's actions, **grouped** (decided on 2026-10-06). The cards and units that are the source of an action are highlighted (the card played, the attacker, the interceptor); click one: its possible targets light up, and its actions without a target (an intercept, Kindle) show as buttons with their `label`. Clicking a target sends the action; when several actions share that target (with and without Overcharge), they show as buttons instead. Actions without a source (keep hand, mulligan, end turn, don't intercept) are plain buttons. The client still sends the index of the chosen action and never builds one: it only filters the decision's list. When `waitingFor` is set: "Waiting for your opponent (intercept)…";
  - **game log**: every event's `text` with its rule IDs (`[8.1] Sprout #12 takes 3 damage.`), newest at the bottom;
  - result banner and a "New game" link.
- **GameSocketService**: connects with the token, exposes the view and the log as signals, sends `act` with a `requestId`, shows `rejected` messages, sends `sync` when versions have a gap, and reconnects with backoff; the `state` the server sends on every connection resyncs it.
- **Bot pacing** (decided on 2026-10-06): the client shows queued updates one by one, about 400 ms apart, so a bot's turn can be followed. The server keeps `shardbound.bots.step-delay = 0`.
- Tests: the socket service's message handling (update, gap → sync, rejected, reconnect), and a smoke test per page.

Settled on 2026-10-07, for slice 3:

1. **Card text on screen.** A card in hand shows its full text. A unit shows only its lines that are not attacks (its keywords and abilities, auras included), since its attacks are already listed with their cost and damage; each attack's own line is its tooltip. A relic shows all its lines. The lines and their kinds come from `GET /api/cards` (section 9); the client only picks which kinds to show.
2. **Answering `CHOOSE_CARDS`** (cards to discard, units to sacrifice). When each action names a single card or unit, that card or unit is highlighted and clicked like a target. When the actions are combinations of several, they stay buttons with the engine's labels. A sacrifice *cost* is part of the `PlayCard` action, so it keeps the grouping above: the card, then its target, then a button per sacrifice when several match.
3. **What the browser can show.** No real card has Discard, a Sacrifice effect, Return to hand, Freeze outside a Fracture step, or a cost aura yet. Those effects are tested in the engine with `test.*` cards, and the client's handling of `CHOOSE_CARDS` with unit tests. In the browser, the sacrifice choice to play is the sacrifice cost of Pyre Offering and Flamebound Zealot.

---

## 15. Rule questions settled with the maintainer

These gaps were found while writing this spec, then settled with the maintainer on 2026-10-05 (Q14–Q18 were found while starting the implementation). Each answer is a rule in the rulebook, and section 8 above implements it. `docs/rules/12-open-points.md` lists them too.

| # | Question | Decision | Rule |
|---|---|---|---|
| Q1 | When does the game end? | As soon as an atomic step leaves a player at 0 HP or less; whatever has not resolved never resolves. Both at 0 from the same effect → draw. | 1.6 |
| Q2 | An action before its triggers? | An action or an ability resolves completely before the abilities it triggered ("Attack" abilities excepted, 9.9). | 9.11 |
| Q3 | Attack whose target left the board | It still takes place: no intercept, effects on the target do nothing, the others apply, it counts as the unit's attack. | 7.9 |
| Q4 | Temporary defense malus expiring | The unit gets back what it lost: max and current defense both go back up (5/5 → 3/3 → 1/3 → 3/5). | 8.17 |
| Q5 | Attack damage bonuses | Apply to every damage effect of the unit's attack abilities; not to its other abilities, nor its echoes. | 8.18 |
| Q6 | Cost computation | Sum everything (printed or step cost, cost auras, −2 for Overcharge), then floor the total at 0 once. | 6.8 |
| Q7 | Not enough units to sacrifice | A sacrifice is never partial: a card or an attack asking for more sacrifices than its controller can make cannot be played or used. No sacrifice, no ability: a triggered ability or an echo whose sacrifices cannot be made does nothing at all. | 8.16, 8.22 |
| Q8 | Full hand | Recall into a full hand does nothing; a Fracture card returning to a full hand goes to the graveyard and loses its progress. | 8.20, 11.2.7 |
| Q9 | Freeze duration | The rest of the current turn plus the next turn; the unit thaws at the start of the turn after. Rule 8.10 reworded. | 8.10 |
| Q10 | "Until end of turn" | The end of the current turn, whoever's turn it is. | 8.19 |
| Q11 | 0 damage | Damage is floored at 0, and a 0-damage hit still happens (event with amount 0). | 8.15 |
| Q12 | Echo rounding of negative values | Toward 0: Echo 50 on −3 gives −1. | 11.1.10 |
| Q13 | Spell targets | All chosen when the spell is played; an invalid one makes its effect do nothing; a spell with a targetless effect stays playable. | 10.5 |
| Q14 | Choices during the opponent's turn | Intercepting is no longer described as the only thing a player does then; they also make the choices rules and effects ask of them. Rule 5.5.2 reworded. | 5.5.2 |
| Q15 | Negative unit defense | A unit's defense never goes below 0; a player's HP can. | 6.9 |
| Q16 | Sacrifice cost on a full board | Playable if the sacrifice really frees a place (at least one sacrificed unit is not anchored). | 3.8 |
| Q17 | Targets of triggered abilities and echoes | All chosen when the ability starts resolving, in printed order, like spells. | 10.6 |
| Q18 | Attacker leaving the board before its attack | The attack does not take place; the cost stays paid. | 7.10 |
| — | Stat aura malus ending | Gives back what it took, like 8.17. | 8.21 |

## 16. Testing strategy

**Core**

- **Rule tests**, built with `ScenarioBuilder` and `ScenarioRunner`. The display name starts with the rule ID: `@DisplayName("11.3.2 — an anchored unit cannot be destroyed by Tempest")`. Assert on the final state **and** on the events, including their rule IDs.
- **Rulebook coverage test** (slice 4): reads every rule ID (`**N.N**` or `**N.N.N**`) from `docs/rules/*.md` and every test display name. It fails if a rule from sections 1–11 has no test, except rules in an explicit allowlist with a reason (purely descriptive rules such as 6.1).
- **Content tests**: the loader loads every card and deck; the deck validator agrees with the Python content tests; the text renderer golden tests (section 9).
- **Full-game fuzz**: 1,000 games RandomBot vs RandomBot over both starter decks, with different seeds. Every game ends with a result. After **every** step, invariants hold: HP ≤ 50; hand ≤ 10; at most 6 units and 3 relics per player; Shards ≥ 0; instance ids unique; every card in exactly one zone; links mutual; every listed legal action can be applied without an exception; the deciding player is the one the decision names.
- **Determinism**: same seed, decks and bot seeds → identical event lists and final state.
- **Redaction**: a view never contains the opponent's hand or any deck order; redacted events never contain the hidden card.
- Performance smoke test: 1,000 random games run within a minute on a laptop (report the number).

**API**

- The domain services with a recording notification DAO: human vs bot to the end; stale and foreign decisions rejected; human vs human with a mid-turn intercept; the join as version 1; the state and the missed updates of a seat.
- **Two instances**: two complete instances, wired by hand, sharing one game DAO and one notification DAO, with fake WebSockets. A human vs human game where each player is connected to a different instance, mid-turn intercept included; the same `act` sent to both instances at once is applied once, the other gets `stale_decision`; a bot game whose messages alternate between instances.
- A `GameSession` comes back unchanged through the adapter and the in-memory DAO's JSON (state, seats, logs, outbox), so a shared store can hold it.
- `ArchitectureTest` (ArchUnit): controller → domain ← adapter → dao, and no transport or JSON in the domain.
- REST controllers with MockMvc (status codes, Problem Details).
- WebSocket: an integration test with a real WebSocket client on a random port: connect, receive `state`, play, receive `update`; a bad token is refused; a second connection replaces the first.

**Frontend**: section 14.

---

## 17. Slices

One branch and one pull request per slice; move to the next slice only once CI is green and the maintainer agrees. PR description: what, why, how it was tested, rule IDs covered, open questions.

Until slice 4 is done, cards using an effect, trigger or keyword that is not implemented yet are **not playable** (they never appear in legal actions), so fuzz games still run. A test lists the unsupported cards; it must be empty at the end of slice 4.

### Slice 1 — Engine foundation (`feat/engine-foundation`)

- Maven Wrapper, parent POM, `core` and `api` modules (`api` is an empty Spring Boot app that starts), Maven Enforcer, `engine.yml` CI.
- Content loading and deck validation; ids, RNG, state, views, events with redaction and descriptions, `ScenarioBuilder` / `ScenarioRunner` (enough for the tests).
- Rules: sections 1, 2, 3, 4, 5 (mulligan included), 6, 7 (intercept included, plus 9.9 Attack abilities), the resolution model (section 7), triggers 9.2–9.6, 9.8, 9.10.
- Effects: Damage, Destroy, Heal, Draw, Summon.
- RandomBot, full-game fuzz, determinism test.
- Done when: `./mvnw verify` is green locally and in CI; 1,000 random games end; each implemented rule has its tests.

### Slice 2 — Game server and test frontend (`feat/game-server`)

- REST (section 12), WebSocket protocol and session server (section 13), human vs bot and human vs human.
- Angular app (section 14), `frontend.yml` CI, commands in `CLAUDE.md`.
- Done when: the maintainer can play a full game against RandomBot in the browser; two browsers can play each other, including an intercept during the other player's turn; reloading the page resumes the game.

### Slice 3 — All effects (`feat/engine-effects`)

- Effects: Sacrifice (effect and cost), Modify, Discard, Return to hand, Freeze, Gain Shards, Recall, Auras (stats and cost).
- Card text rendering (section 9) and the `text` field of `GET /api/cards`; the frontend shows it.
- Done when: every effect has tests per rule; golden text tests pass.

### Slice 4 — Keywords (`feat/engine-keywords`)

- Echo, Fracture, Anchor (doom included), Overcharge, Link (the effect and damage sharing).
- Rulebook coverage test. No unsupported card left.
- Done when: the coverage test passes; every card of both starter decks is playable; fuzz and determinism tests still pass.

### Slice 5 — Determinization, greedy bot, scenarios (`feat/engine-bots`)

- Determinizer, Evaluator, GreedyBot; `greedy` in `GET /api/bots` and selectable in the frontend.
- `ScenarioBuilder` and `ScenarioRunner` polished as a public API, documented in a short README in `engine/core`.
- Done when: GreedyBot wins at least 60% of 200 seeded games against RandomBot (or the maintainer accepts the reported numbers); a test proves the Determinizer never copies the real hidden cards (it must work from a view alone).
