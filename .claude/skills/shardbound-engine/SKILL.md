---
name: shardbound-engine
description: How Shardbound's Java rules engine (engine/core) works and how to call it — the GameEngine API (newGame, apply, decision, view, eventsFor), immutable states, fully specified actions answered by index, decisions, events carrying rule IDs, player views and redaction, the resolution model (steps, trigger queue, state check), determinism, the scenario service, bots and JSON states. Use this whenever you write code that uses the engine (the game server, a bot, a test, a simulation, a scenario or answer key, a Python bridge), need to understand, explain or debug what the engine does, read a rule trace, or find where something lives in engine/core — even for a quick question about one engine class. For changing the engine itself, also use shardbound-engine-dev.
---

# The Shardbound engine

`engine/core` is the executable version of the rulebook (`docs/rules/`). It is pure Java 21 with Jackson as its only dependency: no Spring, enforced by the build. It never decides anything. It applies the rules, lists the legal actions and reports what happened, with the ID of every rule applied.

It serves four consumers, so its API is shaped for all of them, not just the UI (design doc §3.4):

| Consumer | What it uses |
|---|---|
| Game server (`engine/api`) | `newGame`, `apply`, `view`, `eventsFor`, `GameJson` |
| Bots | `Player.choose(PlayerView, Decision)` → one of the decision's actions |
| Scenario service (tests, Arbiter answer keys) | `ScenarioBuilder`, `ScenarioRunner`, the unredacted events |
| MCTS (later) | immutable states to branch from, determinization from a view |

## Vocabulary: one word, one thing

These words are easy to blur, and most confusions about the engine come from blurring them.

| Word | What it is | Type | Example |
|---|---|---|---|
| Card definition | What a card **is**: static, shared by every game | `content.UnitCard`, `SpellCard`, `RelicCard` | Ash Warden: cost 3, defense 6, Cinder Bite… |
| Card instance | One copy of a card in one game | `state.CardInstance` (instance id + card id + owner) | `#15`, an Ash Warden of P1 |
| Unit | A card instance on the board, with its game state | `state.Unit` | `#15` at 2/6 defense, has attacked this turn |
| Decklist and deck | The registered list, and the shuffled draw pile | `content.Deck`; `PlayerState.deck` | "ember-starter"; the 23 cards left to draw |
| Decision | A **question** the game asks one player, with its options | `decision.Decision` | "P2, do you intercept?" |
| Action | One **answer**, always one of the decision's options | `action.Action` | `Intercept(#38)` |
| Step | One piece of **work** left to do | `resolution.Step` | `ResolveAttack(…, phase INTERCEPT)` |
| Resolution | The **to-do list**: the steps, plus the triggered abilities waiting in the queue | `resolution.Resolution` | |
| Transition | What one engine call returns | `rules.Transition` | the new state + the events |
| Event | What **happened**, with the rule IDs applied | `event.GameEvent` | `UnitDamaged[8.1]` |
| View | What one player may see | `view.PlayerView` | own hand, the opponent's hand count |

Two traps:

- An **attack ability** is the attack printed on a unit, with its cost and effects ("Gore: deal 2 damage"). An **"On attack" ability** is a *triggered* ability that fires when the unit attacks (trigger `attack` in the card format, rule 9.9). They are resolved at different moments of an attack.
- The state holds both the to-do list (`resolution`) and the question (`pending`). When a step needs an answer, it waits at the front of the to-do list **and** a decision is pending: that is what "paused" means. No flag says it.

## The mental model in six points

1. **A `GameState` is immutable and complete**: both hands, deck orders, the pending work, the RNG state. It never leaves the server. Clients and bots only get a `PlayerView`.
2. **`apply(state, action)` returns a `Transition`**: the new state and the events that led to it. The old state stays valid, so branching is free.
3. **The engine lists every legal action**, fully specified with targets included, in a canonical order (spec §6.2). The list is computed once, when the decision is created, and stored in the state (`state.pending()`). A player answers by picking one, and `apply` checks it with `contains`, by value equality, without recomputing anything. The client never builds an action.
4. **A `Decision`** says who must choose (`player`), what kind of choice it is (`MULLIGAN`, `MAIN`, `INTERCEPT`, `CHOOSE_TARGET`…) and the options (`actions`). It can belong to the non-active player in the middle of a turn, for example to intercept. The engine only asks when there are at least 2 options, except for `MAIN`, which is always asked. **There is at most one decision at a time**: the engine stops as soon as one is pending, so choices always come one after another, never at once.
5. **Events are the rule trace.** Each `GameEvent` has `rules()` (rule IDs, never empty), a `visibility()`, and a `redacted()` form for the players who may not see it. Events name the cards they involve (`CardInstance`), so a log reads without the state.
6. **Same seed, same decks and same actions give the same game**, events included. Bots have their own RNG and never touch the game's.

## The API

```java
Content content = ContentLoader.load(contentDir);      // contentDir = the repository's content/ folder
GameEngine engine = new GameEngine(content.catalog());

Transition t = engine.newGame(new GameSetup(content.deck("ember-starter"), content.deck("root-starter"), seed));
List<GameEvent> log = new ArrayList<>(t.events());
GameState state = t.state();

for (Optional<Decision> d = engine.decision(state); d.isPresent(); d = engine.decision(state)) {
    PlayerId decider = d.get().player();
    PlayerView view = engine.view(state, decider, log);           // redacted for that player
    Action action = players.get(decider).choose(view, d.get());   // must be one of d.get().actions()
    t = engine.apply(state, action);
    log.addAll(t.events());
    state = t.state();
}
GameResult result = state.result().orElseThrow();      // Win(winner, reason) or Draw(reason)
```

| Method | Contract |
|---|---|
| (persistence) | The engine stores nothing: it is a library. Whoever calls it keeps the returned state, decision included: the game server in its `GameRepository`, a test in a variable. |
| `newGame(GameSetup)` | Validates both decks (illegal deck → `IllegalArgumentException` with rule IDs), runs the setup (5.1) and stops at the first mulligan decision. |
| `decision(state)` | The pending decision, empty once the game is over. |
| `apply(state, action)` | `IllegalActionException` if the action is not one of the decision's actions. Runs until the next decision or the end of the game, which can take many steps: ending a turn runs the opponent's whole start of turn. |
| `resume(state)` | Runs a state built outside a game (a scenario) until its first decision. |
| `view(state, viewer, history)` | What `viewer` may see: own hand, the opponent's hand count, public zones, the decision only if it is theirs (otherwise `waitingFor`), and the redacted history. |
| `eventsFor(events, viewer)` | The same events, redacted for `viewer` (each event's `seenBy(viewer)`: as it is if its `visibility()` lets the viewer see it, `redacted()` otherwise). Send these to a player, never the raw list. |

Rule of thumb for any consumer: **talk to players only through `view` and `eventsFor`**. Anything built from a raw `GameState` or from unredacted events leaks hidden information. The design doc treats this as cheating: it inflates a bot's Elo and shows the human's hand.

## Where things live (`fr.daliush.shardbound.core`)

| Package | Contents |
|---|---|
| `content` | Card and deck model (sealed `CardDefinition`, `Effect`, `TargetSpec`…), `ContentLoader` (strict), `DeckValidator`, `CardCatalog`. `content.json` holds the parsers. |
| `state` | `GameState`, `PlayerState`, `Unit`, `Relic`, `HandCard`, `CardInstance`, `InstanceId`, `Shards`, `GameResult` |
| `action` / `decision` | `Action` (sealed), `TargetRef`, `Decision`, `DecisionKind` |
| `event` | `GameEvent` (sealed, all events nested, grouped by theme), `Visibility`, `Redaction`, `EventTarget` |
| `view` | `PlayerView`, `SelfState`, `OpponentState`, `WaitingFor`, `PlayerViews` |
| `resolution` | The pending work stored in the state: `Step` (sealed), `QueuedTrigger`, `EffectSource`, `EffectList`, `Resolution` |
| `rules` | `GameEngine` (the facade), `GameSetup`, `Transition`, then one sub-package per rulebook area: `game` (working copy, loop, state check), `setup`, `turn`, `play`, `combat`, `effect`, `trigger`, `board` |
| `scenario` | `ScenarioBuilder`, `ScenarioRunner`, `ScenarioResult`, `Choices`, `Pick` |
| `text` | `EventDescriber` (event → English sentence), `ActionDescriber` (action → button label) |
| `bot` | `Player`, `RandomBot` (later: `GreedyBot`, `Determinizer`) |
| `json` | `GameJson`: a state or an event log to JSON and back |
| `random` | `SplitMix64`, the only randomness the engine uses |

## How one `apply` is resolved, in short

1. Check that the action is one of the pending decision's actions.
2. Thaw the state into a mutable working copy, `rules.game.Game`, which also collects events.
3. Feed the action in. A `MULLIGAN` answer goes to `Mulligans`. A `MAIN` answer pushes a step (play a card, attack, end the turn). Any other answer resumes the step that asked the question.
4. `Resolver.run` loops: run the front step, or start the next queued ability once no step is left. When nothing is left, it asks for `MAIN`. It stops as soon as a decision is pending or the game is over.
5. After every step, `StateCheck` runs: units at 0 defense die together, the abilities raised during the step join the queue in order (9.8, 9.10), and a player at 0 HP ends the game at once (1.6).
6. Freeze the working copy back into a `GameState`, and return it with the events.

For the details — the step types, the attack phases, how a decision pauses and resumes a step, ordering, a worked trace — read `references/resolution.md`.

## Using the scenario service

Build any board without playing a game, play choices named by card, and get the exact outcome with its rule trace. Rule tests and Arbiter answer keys both come from it.

```java
GameState start = ScenarioBuilder.of(catalog)
        .shards(P1, 6).hand(P1, "ember.cinderfall").unit(P1, "neutral.shardling")
        .unit(P2, "ember.cinderling").unit(P2, "neutral.shard-construct", unit -> unit.defense(5))
        .build();
ScenarioResult result = new ScenarioRunner(engine).run(start,
        play("ember.cinderfall"),                                            // Choices.play, attack, endTurn…
        attack("neutral.shardling").on(unit("neutral.shard-construct")));   // Pick.unit, player, relic

result.state(); result.events(); result.decisions(); result.pending(); result.findUnit("ember.cinderling");
```

Defaults worth knowing: turn 3, P1 active and first player, 50 HP, 0 Shards, **empty decks**, so a draw costs fatigue HP. Units have arrived on an earlier turn, so they can attack. Instance ids and arrival order follow the order of the calls. A `Choice` that matches zero or several actions throws, and the message lists the decision's actions. More examples: `references/cookbook.md`.

## What is implemented

The engine is built in the slices of `specs/phase-2-engine.md` §17. Cards that need an effect, trigger or keyword not implemented yet are never offered as legal actions (`rules.play.EngineSupport`). `EngineSupportTest` holds the live list of those cards. Check both before relying on a card in a test or a demo.

## Further reading

- `references/resolution.md`: the resolution model step by step, with a worked trace.
- `references/cookbook.md`: code for each consumer (game loop, bot, view, scenario test, JSON storage, logs and labels).
- `specs/phase-2-engine.md`: the contract (sections 4 to 11) and the rule-by-rule implementation notes (section 8).
- `specs/phase-2-examples.md`: what the engine and the API return, on a real situation.
- Changing the engine: the `shardbound-engine-dev` skill. Rules questions: the `shardbound-rules` skill.
