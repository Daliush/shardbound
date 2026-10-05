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
- **Start by asking the questions of section 15**, all at once, numbered, with the proposed default. Record the answers in the rulebook (and remove *(proposed)* markers) before implementing the affected behavior.
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

MCTS, gRPC and Python clients, any database or `repository` module, user accounts and authentication, timers for human vs human games, replay endpoint, Docker and deployment, polished UI, animations, deck builder, collection, boosters, MCP server, anything AI.

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
    └── src/main/java/fr/daliush/shardbound/api/
        ├── rest/       CardsController, DecksController, BotsController, GamesController, error handling
        ├── ws/         GameWebSocketHandler, handshake, protocol messages, PlayerConnections
        ├── session/    GameSession, Seat, GameSessionService, GameNotifier (port), GameRepository (port), in-memory repository
        ├── dto/        view, decision, action, event DTOs and mappers
        └── config/     WebSocket and content configuration
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
cd engine && ./mvnw -pl api spring-boot:run     # run the server on http://localhost:8080
cd frontend && npm start                        # run the client on http://localhost:4200 (proxies /api and /ws)
cd frontend && npm test                         # client unit tests
```

### 3.3 Locating the content

- `core` loads content with `ContentLoader.load(Path contentDir)`, where `contentDir` is the repository's `content/` folder (it contains `cards/` and `decks/`).
- Tests find it by walking up from the working directory until they find `content/cards/card.schema.json`. Put that lookup in a small test utility.
- `api` reads the property `shardbound.content-dir`. When it is not set, use the same upward lookup. Fail at startup with a clear message if the folder is not found.

### 3.4 Configuration properties (`api`)

| Property | Default | Meaning |
|---|---|---|
| `shardbound.content-dir` | auto-detected | Path to the repository's `content/` folder |
| `shardbound.sessions.finished-ttl` | `30m` | How long a finished game stays in memory |
| `shardbound.sessions.idle-ttl` | `2h` | How long a game without any action stays in memory |
| `shardbound.bots.step-delay` | `0ms` | Optional pause between two bot actions (the client can also pace them) |

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
    boolean attackedThisTurn, boolean interceptedThisTurn,
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
| `UnitSacrificed(unit)` | 8.3 | public |
| `UnitArrived(unit)`, `RelicArrived(relic)` | 6.3, 6.5 | public |
| `TokenSummoned(unit)`, `SummonFailed(player)` | 8.9, 3.4 | public |
| `AttackDeclared(attacker, attackIndex, target)` | 7.4 | public |
| `AttackIntercepted(originalTarget, interceptor)` | 7.5 | public |
| `DamageDealt(target, amount)`, `DamageShared(from, to, amount)` | 8.1, 11.5.2 | public |
| `Healed(target, amount)` | 8.4 | public |
| `Modified(unit, attackDamage, defense, duration)`, `ModifierExpired(unit, …)` | 8.5, 5.4.2 | public |
| `Frozen(unit, throughTurn)` | 8.10 | public |
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

`core/text/EventDescriber` turns an event into an English sentence from a viewer's point of view ("You draw Spark Dart.", "Your opponent draws a card.", "Sprout #12 takes 3 damage."). The API sends it with each event; it will also feed the LLM work later.

---

## 7. Core: resolution model

`apply` is a small interpreter over the work stored in `GameState.resolution`:

```java
record Resolution(List<Step> steps, List<QueuedTrigger> triggerQueue) {}
```

- **Steps** are the remaining work of what is currently resolving: an action, a turn transition, or one triggered ability. They run front first, and a step can push sub-steps to the front.
- **The trigger queue** is FIFO. Triggered abilities wait there and run only when `steps` is empty: **an action fully resolves before the abilities it triggered** (question Q2).
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
    if a player has hp <= 0: end the game now (1.2, 1.3, question Q1)
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

For each rulebook section: how the engine implements it. **[Q n]** marks a point to confirm with the maintainer first (section 15); implement the proposed default only after confirmation.

### Section 1 — The game

- **1.1** `hp = 50`; max HP 50.
- **1.2 / 1.3** After every atomic step, if a player has `hp <= 0`, the game ends at once: one player at 0 → `Win(other, HP)`; both → `Draw(DOUBLE_KO)`. Steps and triggers still waiting are dropped **[Q1]**. Emit `GameEnded`.
- **1.4** Each draw from an empty deck: `fatigue += 1`, `hp -= fatigue`. This is HP loss, not damage: Link does not share it. Emit `HpLost(reason FATIGUE)`. It applies to every draw: start of turn and Draw effects.
- **1.5** After the end-of-turn steps of global turn 100 (each player's 50th turn), if no result: `Draw(TURN_LIMIT)`.

### Section 2 — Deck building

- **2.1–2.4** `newGame` validates both decks with `DeckValidator` (same checks and messages as `content/tests/contentlib.py`). Illegal deck → `IllegalArgumentException`.

### Section 3 — Zones

- **3.3** Drawing with 10 cards in hand: the drawn card goes to the graveyard (`CardDiscarded(reason OVERDRAW)`, public).
- **3.4** A unit cannot be played with 6 own units on the board, nor a relic with 3 relics (not a legal action). Summon with a full board: nothing (`SummonFailed`).
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
- **5.2** Start of turn, in order: **5.2.1** end of Anchor protection for the active player's units; **5.2.2** Shards (4.1, 4.2); **5.2.3** draw 1 (except the first player's very first turn); **5.2.4** "Turn start" abilities of the active player's units and relics (by arrival order) go to the queue. At the same moment, reset `attackedThisTurn` and `interceptedThisTurn` on every unit: "this turn" in 7.2 and 7.5 means each turn, the opponent's included. When everything has resolved: `MAIN` decision.
- **5.3** `MAIN` lists every legal `PlayCard`, `Attack` and `EndTurn`. It is always asked, even when `EndTurn` is the only option.
- **5.4** `EndTurn`: **5.4.1** "Turn end" abilities of the active player's cards to the queue, then drain it; **5.4.2** remove `END_OF_TURN` modifiers (8.5 for defense); **5.4.3** destroy the active player's doomed units that are no longer protected and still have 0 defense (11.3.5); **5.4.4** `shards = 0`; then 1.5; then start the opponent's turn (`turn += 1`).
- **5.5** The non-active player never gets `MAIN` or `PlayCard`: only `INTERCEPT` and the `CHOOSE_*` decisions that rules give them.

### Section 6 — Cards

- **6.3 Playing a card** (`PlayCard`), in order:
  1. Compute the cost **[Q6]**: printed cost (or the Fracture step cost) + cost auras, floored at 0; if overcharged, −2, floored at 0. Pay it.
  2. Pay the sacrifice cost: each unit in `sacrificed` dies (`UnitSacrificed`; Death and Departure triggers are queued). An Anchor-protected unit counts as paid but stays (11.3.3).
  3. Overcharge: `lockedNextTurn += 2` (11.4.2).
  4. Unit or relic: arrives on the board (arrival sequence, `arrivedTurn = turn`, `defense = maxDefense` from the card, `anchorProtected` if it has Anchor), auras reconcile; its Arrival abilities are queued. Spell (9.1 Cast): resolve its effects (or the step's effects) in printed order, using the chosen targets; then **11.2** for Fracture, otherwise the spell goes to the graveyard (`SpellResolved`).
- **6.5** Arrival: `defense = maxDefense`, both including the auras that apply (reconciliation).
- **6.6** Handled by the state check.
- **6.7** Returning to hand resets everything: a new `HandCard` with the same instance id, no modifiers, no damage, no link, no flags.

### Section 7 — Combat

- **7.1** A unit has 1 or 2 attack abilities. An attack "has a target" if one of its effects targets `ATTACK_TARGET`.
- **7.2** An `Attack` is legal if: the attacker belongs to the active player; `arrivedTurn < turn`; it is not frozen (`turn > frozenThroughTurn`); `!attackedThisTurn`; the player can pay that attack's cost. Overcharge never applies to attacks (11.4.5).
- **7.3** Valid targets of an attack with a target: every enemy unit (frozen, anchored and doomed units included). If the enemy has no unit: the enemy player. Relics are never attack targets.
- **7.4 / 9.9** Resolving an `Attack`:
  1. pay the cost, set `attackedThisTurn`, emit `AttackDeclared`;
  2. resolve the attacker's "Attack" abilities **right away** (not through the queue: rule 9.9 places them before the intercept decision; their consequences, such as deaths, still queue normally);
  3. if the target is a unit still on the board and the defender has at least one eligible interceptor: `INTERCEPT` decision for the defender (7.5);
  4. resolve the attack's effects in printed order: `ATTACK_TARGET` is the (possibly redirected) target; `SELF` is the attacker, if still on the board; `YOU` is its controller; `ALL_ALLY_UNITS` are its controller's units; damage effects add the attacker's effective damage bonus (8.5, **[Q5]**).
  - If the target left the board before step 3 (for example killed by an "Attack" ability): no intercept, effects on `ATTACK_TARGET` do nothing, the other effects apply, and it still counts as the unit's attack **[Q3]**.
- **7.5** Eligible interceptors: the defender's units other than the target, not frozen, `!interceptedThisTurn` (anchored and doomed units included). Intercepting sets `interceptedThisTurn` and emits `AttackIntercepted`. Spells, abilities and echoes are never intercepted.
- **7.6** No retaliation.
- **7.7** Spell targets are not limited by 7.3.
- **7.8** An attack without a target never asks for an intercept, still counts as the unit's attack and still triggers "Attack" abilities.

### Section 8 — Effects

**Resolving targets** at resolution time, for an effect controlled by C:
- Chosen targets (`ALLY_UNIT`, `ENEMY_UNIT`, `ANY_UNIT`, `ANY_PLAYER`, relic specs): for a spell, from `PlayCard.targets`. For a triggered ability or an echo, a `CHOOSE_TARGET` decision for C when the ability resolves (10.2). A chosen target that is no longer valid when the effect resolves → that effect does nothing (10.3) **[Q13]**.
- `RANDOM_ALLY_UNIT` / `RANDOM_ENEMY_UNIT`: drawn with the game RNG among the valid units at resolution; none → nothing.
- `SELF`: the source unit or relic if it is still on the board; otherwise nothing.
- `ALL_*`: the matching units when the effect starts; each receives it in arrival order; then one state check (simultaneous deaths).
- `YOU` / `OPPONENT`: relative to C. `ATTACK_TARGET`: only inside attacks and echoes.
- Relics can only be targeted by Destroy and Return to hand (10.4).

**Effects:**
- **8.1 Damage** on a unit: Link first (11.5.2), then `defense -= amount`; on a player: `hp -= amount`. An amount of 0 or less does nothing: no event, no Link share **[Q11]**.
- **8.2 Destroy**: a unit dies, unless Anchor-protected (`AnchorPrevented`). A relic goes to the graveyard (`RelicDestroyed`); its Death and Departure abilities trigger.
- **8.3 Sacrifice** (as an effect): C chooses `count` of their own units (`CHOOSE_CARDS` when C has more units than `count`; otherwise all of them) **[Q7]**. They die (Death triggers). An Anchor-protected unit counts but stays (11.3.3).
- **8.4 Heal**: a unit: `defense = min(maxDefense, defense + amount)`, and if it was doomed and is now above 0, `DoomLifted` (11.3.4). A player: `hp = min(50, hp + amount)`.
- **8.5 Modify**: `+X` goes into the attack damage bonus; `+Y` is added to both `maxDefense` and `defense`. A debuff can bring `defense` to 0, and the state check handles it. Keep a `Modifier` with its duration. When an `END_OF_TURN` modifier expires (5.4.2, at the end of the current turn, whoever's it is **[Q10]**): remove its attack bonus; if `Y > 0`: `maxDefense -= Y`, `defense = min(defense, maxDefense)`, which never kills; if `Y < 0`: `maxDefense -= Y` (restored), `defense` unchanged **[Q4]**.
- **8.6 Draw**: the target player draws `amount` cards, one at a time (3.3 and 1.4 apply to each).
- **8.7 Discard**: `RANDOM` uses the game RNG; `PLAYER` means the discarding player chooses (`CHOOSE_CARDS` for that player, even during the other player's turn, when their hand has more cards than `amount`; otherwise discard everything). A Fracture card loses its progress (11.2.6).
- **8.8 Return to hand**: a unit or relic goes to its owner's hand, reset (6.7), keeping its instance id. A token vanishes (3.6). With a full hand: graveyard (`SentToGraveyardHandFull`); it does not die, so no Death and no Echo, but Departure triggers. Anchor-protected: prevented.
- **8.9 Summon**: create `count` token units for C, until the board is full (3.4): arrival sequence, `arrivedTurn = turn`, Anchor protection if the token has Anchor; their Arrival abilities trigger.
- **8.10 Freeze**: `frozenThroughTurn` = the global turn number of the next turn of the unit's controller that starts after now: `turn + 1` if the controller is not the active player, `turn + 2` if it is **[Q9]**. A frozen unit cannot attack or intercept.
- **8.11 Link**: two different units, any side, neither already linked (11.5.1). Fewer than two eligible units → nothing.
- **8.12 Gain Shards**: `THIS_TURN`: `shards += amount`, which can exceed `maxShards` for this turn. `MAX`: `maxShards = min(10, maxShards + 1)` (current Shards unchanged).
- **8.13 Recall**: a unit card from C's graveyard (chosen: a `PlayCard` slot for spells, `CHOOSE_TARGET` for abilities) goes to C's hand. With a full hand, nothing happens and the card stays in the graveyard **[Q8]**.
- **8.14 Auras** (continuous abilities of units and relics on the board):
  - *Stat aura*: every unit in the target group gets `+attackDamage/+defense`. **Reconciliation**, run in every state check: compute for each unit the bonus each active aura should give, compare with `appliedAuras`; a new or larger contribution adds to `maxDefense` and `defense`; a removed or smaller one lowers `maxDefense` and caps `defense` at the new max (like an expiring bonus, 8.5); the attack part is just recomputed. A unit arriving under an aura arrives with the bonus (6.5).
  - *Cost aura*: changes the cost of the given card type for the given player while the source is on the board (cost computation, **[Q6]**).

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

- **11.1 Echo**: when a unit **dies** (destroyed or sacrificed; not returned to hand), each of its attacks with `echo` triggers an echo: its **printed** effects (11.1.9) with every numeric value multiplied by X% and rounded down **[Q12]** (damage, heal and draw amounts, Modify values, Summon count, Gain Shards amount); non-numeric effects apply fully. If the attack has a target, the owner chooses a new one with the 7.3 rules (`CHOOSE_TARGET`, automatic if only one option). `SELF` effects do nothing (the unit is dead), `YOU` is the owner. An echo costs nothing, cannot be intercepted, does not trigger "Attack" abilities and does not count as an attack (11.1.4). Order: 9.10; two echoes on one card: `CHOOSE_ORDER` (11.1.8). Chains are natural: echo deaths queue new echoes (11.1.6).
- **11.2 Fracture**: `HandCard.fractureStep` is the next step. A Fracture card is playable only if `lastFractureTurn != turn` (11.2.3); it costs the step's cost. After a step that is not the last, it returns to its owner's hand with `fractureStep + 1` and `lastFractureTurn = turn` (`FractureAdvanced`); with a full hand, it goes to the graveyard and the progress is lost **[Q8]**. After the last step: graveyard (`SpellResolved`). `CardPlayed` shows the step publicly; the opponent's view never shows steps.
- **11.3 Anchor**: on arrival, `anchorProtected = true`; it ends at 5.2.1 of its controller's next turn. While protected: destroy, return to hand and sacrifice have no effect on it (`AnchorPrevented`; a sacrifice still counts as paid); damage and debuffs apply; at 0 defense it becomes `doomed` instead of being destroyed. A doomed unit can still attack and intercept; healed above 0 → `DoomLifted`; back to 0 while still protected → doomed again. At 5.4.3 of the turn during which its protection ended, a doomed unit still at 0 is destroyed; "Turn end" abilities resolve before that (11.3.5). After the protection: normal rules, so a unit reaching 0 is destroyed immediately (11.3.6). A unit doomed during its own arrival turn is **not** destroyed at the end of that turn: its protection has not ended yet.
- **11.4 Overcharge**: for cards with the keyword, both `PlayCard(overcharge = false)` and `PlayCard(overcharge = true)` are offered when affordable. Cost: **[Q6]**. Lock: +2 per overcharged card, stacking; refill per 4.2; any excess is lost (11.4.4).
- **11.5 Link**: `linkedTo` on both units. Damage `n` to a linked unit: it takes `ceil(n / 2)`, its partner `floor(n / 2)`; the transferred part is not shared again (11.5.2). Only damage is shared (11.5.3). When either unit leaves the board, both links are cleared (`LinkBroken`, 11.5.4).

---

## 9. Card text rendering (`core/text/CardTextRenderer`)

Renders a card's rules text from its data and `content/cards/text-templates.json` (read its `$comment` first).

- Line order: keywords line (`"Anchor."`, `"Overcharge."` joined, in the card's keyword order), sacrifice cost, attacks (named: `"{name} ({cost} {Shard|Shards}): {effects}"`; unnamed: `"Attack (…): …"`; then `" Echo {x}."`), Fracture header and steps, spell effects, abilities (`"{trigger}: {effects}"`; continuous abilities without a label).
- Effect sentences use the templates. Variant keys: `heal` → `unit` or `player` from the target class; `modify` → duration; `draw` → target; `discard` → `"<target>.<choice>"`; `return_to_hand` → `single` or `group` (`ALL_*` targets are groups); `gain_shards` → mode; aura → `stats`, or `cost.you` / `cost.opponent` with `aura_cards` and `aura_change`.
- `{target}` uses the `targets` phrases; `self` gives `"this unit"` or `"this relic"`; `{token}` is the token's name.
- `{word|words}` picks singular when the closest preceding number is 1. Modify and aura values are always signed (`+2`, `-1`, `+0`). The first letter of each sentence is capitalized.
- **Golden tests**: the two examples in `content/cards/README.md` (Ash Warden and Moonpull) must render exactly as shown there. Add one test per template key.
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
| `GET` | `/api/cards` | — | `[{ id, name, faction, type, cost?, defense?, token, keywords, text: [lines], flavor? , fracture?: [{ step, cost }] }]` |
| `GET` | `/api/decks` | — | `[{ id, name, description?, faction, cards: [{ card, count }] }]` |
| `GET` | `/api/bots` | — | `["random", "greedy"]` (only the bots implemented so far) |
| `POST` | `/api/games` | `{ "deck": "ember-starter", "opponent": { "type": "bot", "bot": "random", "deck": "root-starter" }, "seed"?: 42 }` | `201 { gameId, playerToken, joinCode?: null, websocketPath: "/ws/games/{id}" }` |
| `POST` | `/api/games` | `{ "deck": "ember-starter", "opponent": { "type": "human" } }` | `201 { gameId, playerToken, joinCode, websocketPath }` |
| `POST` | `/api/games/{id}/join` | `{ "joinCode": "…", "deck": "root-starter" }` | `200 { gameId, playerToken, websocketPath }` |

- The creator always takes seat P1, and the bot or the joiner seat P2. Who plays first is decided by the RNG (5.1.1).
- A bot game starts at creation. A human vs human game starts when the second player joins; before that, the creator's view has status `waiting_for_opponent`.
- `seed` is optional (random when missing). It is never returned while the game runs.
- `playerToken` and `joinCode`: 32 random bytes from `SecureRandom`, base64url. Compare tokens in constant time.
- Validation errors (unknown deck, unknown bot, illegal deck): `400`. Unknown game: `404`. Wrong join code, or game already full: `409`.

---

## 13. API: WebSocket game protocol and session server

### 13.1 Connection

- URL: `ws://host/ws/games/{gameId}?token={playerToken}`.
- A `HandshakeInterceptor` resolves the game and the seat from the token. Unknown game → reject the handshake with `404`; bad token → `401`.
- On connection, the server immediately sends a `state` message.
- One connection per seat: a new connection with the same token replaces the old one, which the server closes with code `4409` (`"replaced by a newer connection"`).
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
- `view.version` increases by 1 with every applied action. If a client receives a version that is not `last + 1`, it sends `sync`.
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
interface HandCardView extends CardRef { fractureStep: number | null; }  // 1-based next step, null if not Fracture
interface RelicView extends CardRef { controller: Side; }
interface UnitView extends CardRef {
  controller: Side; token: boolean;
  defense: number; maxDefense: number;
  attacks: { index: number; name: string | null; cost: number; damage: number | null; hasTarget: boolean; echo: number | null }[];
  arrivedThisTurn: boolean; attackedThisTurn: boolean; interceptedThisTurn: boolean;
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

### 13.4 Session server (`api/session`)

- `GameSession`: game id; two `Seat`s (`PlayerId`, kind HUMAN or BOT, token for humans, bot instance, deck); the current `GameState`; `version`; the event log (unredacted, in order); status; last activity time.
- `GameSessionService` (no WebSocket types in it): `createGame`, `join`, `connect(gameId, token)` (returns the seat and the `state` payload), `act(gameId, seat, requestId, decisionId, actionIndex)`, `sync(gameId, seat)`.
- **One game processes its messages one at a time, in order**: each session owns a single-threaded executor running on a virtual thread (`Executors.newSingleThreadExecutor(Thread.ofVirtual().factory())`). WebSocket threads only submit work to it, so they never block, even when a bot thinks.
- **Processing `act`**:
  1. If the game is not started or is over: `rejected`.
  2. Recompute the decision from the state. Never trust the client.
  3. If the seat is not the decision's player: `not_your_decision`. If the `decisionId` differs: `stale_decision`. If the index is out of range: `invalid_action`.
  4. `engine.apply`, then `version += 1`, append the events to the log, notify both seats.
  5. While the next decision belongs to a bot seat and the game is not over: the bot chooses, then back to 4 (optional `step-delay` between iterations). Guard against bugs: at most 10,000 bot steps per human action, then fail loudly.
- Human vs human: the same flow; whichever seat holds the decision acts, mid-turn intercepts and echo choices included.
- `GameNotifier` (port): `send(gameId, seat, message)`. The WebSocket adapter implements it with `PlayerConnections`; tests use a recording fake. A seat without a connection simply misses the message and will `sync`.
- `GameRepository` (port) with `InMemoryGameRepository` (`ConcurrentHashMap`). A scheduled task evicts finished games after `finished-ttl` and idle games after `idle-ttl`, shutting down their executors.
- Logging: one line per applied action (game id, seat, decision kind, action label, version).
- Single instance only: sessions live in memory. Several instances would need sticky sessions or a shared store; that is out of scope.

---

## 14. Frontend (`frontend/`)

Minimal and plain: correctness first, no animations required.

- Angular 21 (installed), standalone components, signals, `HttpClient`, RxJS `webSocket()`. No UI library and no state library; plain CSS.
- Dev proxy (`proxy.conf.json`) for `/api` and `/ws` (with `ws: true`) to `http://localhost:8080`, so no CORS configuration is needed.
- **Routes**:
  - `/`: choose your deck (`GET /api/decks`) and an opponent: a bot (`GET /api/bots`, opponent deck) or a human; "Create game". For a human opponent, show the invite link `/join/{gameId}?code={joinCode}`.
  - `/join/:gameId`: choose your deck, join, then go to the game.
  - `/games/:gameId`: the game.
- **Token storage**: `localStorage["shardbound.game.<gameId>.token"]`. Opening `/games/:id` without a token: message plus a link home.
- **Game screen**:
  - opponent: faction, HP, Shards (available / max, locked), hand count, deck count, fatigue, units, relics, graveyard count;
  - you: the same, plus your hand. Each card shows name, cost, rendered text (`GET /api/cards`, cached) and the Fracture step;
  - each unit shows name, defense / max defense, its attacks (name, cost, damage, Echo X) and badges: arrived this turn, attacked, intercepted, frozen, anchored, doomed, linked to #id;
  - **decision panel**: the `prompt` and one button per action (`label`). When `waitingFor` is set: "Waiting for your opponent (intercept)…";
  - **game log**: every event's `text` with its rule IDs (`[8.1] Sprout #12 takes 3 damage.`), newest at the bottom;
  - result banner and a "New game" link.
- **GameSocketService**: connects with the token, exposes the view and the log as signals, sends `act` with a `requestId`, shows `rejected` messages, sends `sync` when versions have a gap, and reconnects with backoff (then `sync`).
- Tests: the socket service's message handling (update, gap → sync, rejected, reconnect), and a smoke test per page.

---

## 15. Questions to confirm with the maintainer (ask them first, all at once)

These are gaps in the rulebook found while writing this spec. Present each with its proposed default. Once answered, add the rule to the rulebook (new IDs at the end of the relevant section; never renumber) and update this spec.

1. **Q1. When the game ends.** As soon as an atomic step leaves a player at 0 HP or less, and the effects and triggers still waiting are dropped. Both players at 0 in the same step → draw (1.3).
2. **Q2. An action before its triggers.** An action (or a triggered ability) resolves completely before the abilities it triggered, which wait in the queue. Example: Pyre Offering sacrificing Cinderling deals its 8 damage before Cinderling's Death ability deals 2 to the opponent.
3. **Q3. An attack whose target disappeared** before it resolves (for example killed by an "Attack" ability): no intercept, effects on the target do nothing, other effects apply, and it still counts as the unit's attack.
4. **Q4. A temporary defense malus expiring** (no card has one yet): max defense is restored, current defense is unchanged.
5. **Q5. Attack damage bonuses** (+X from Modify or auras) apply to every damage effect of an attack ability.
6. **Q6. Cost computation order**: printed cost + cost auras, floored at 0; then −2 if overcharged, floored at 0.
7. **Q7. A Sacrifice effect asking for more units than you have**: you sacrifice all of them; when you have more, you choose which.
8. **Q8. Full hand**: Recall into a full hand does nothing (the card stays in the graveyard); a Fracture card returning to a full hand goes to the graveyard and loses its progress.
9. **Q9. Freeze duration**: "until the end of its controller's next turn" = the next turn of that controller that starts after the freeze. Frozen during the opponent's turn: until the end of the controller's coming turn; frozen during the controller's own turn: through their following turn.
10. **Q10. "Until end of turn"** means the end of the current turn, whoever's turn it is.
11. **Q11. 0 damage** (after maluses) does nothing: no event, nothing shared through Link.
12. **Q12. Echo rounding of negative values**: "rounded down" applies to the size of the number (toward zero): Echo 50 on −3 gives −1. Positive values round down normally.
13. **Q13. Spell targets** are chosen when the spell is played. If one becomes invalid before its effect resolves (because of an earlier effect of the same spell), that effect does nothing. Spells with no valid target for an effect can still be played; that effect does nothing (10.3).

---

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

- `GameSessionService` with a recording `GameNotifier`: human vs bot to the end; stale and foreign decisions rejected; human vs human with a mid-turn intercept; `sync`.
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
