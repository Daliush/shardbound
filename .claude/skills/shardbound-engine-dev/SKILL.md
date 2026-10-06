---
name: shardbound-engine-dev
description: How to develop on Shardbound's Java engine (engine/core) — the workflow from rule ID to merged commit, the design rules that keep the engine immutable, deterministic, serializable and honest about hidden information, step-by-step recipes to add or change an effect, a trigger, a keyword, an event, a step or a mid-resolution decision, how to write rule tests with the scenario service, the clean-code and package conventions, and which docs to keep in sync. Use this whenever you modify, extend, refactor or debug engine code or its tests, implement a slice of specs/phase-2-engine.md, make a card playable, fix a rules bug, or plan engine work — even a one-line change in engine/core. Read shardbound-engine first for how the engine works.
---

# Developing on the engine

The engine is the single source of truth for the game. Bots, the Arbiter's answer keys and the frontend all defer to it, and recruiters read its code. A change is good when it applies one rule of the rulebook exactly, says so in the trace, is covered by a test named after that rule, and reads simply.

Read the `shardbound-engine` skill first (and its `references/resolution.md`): this skill assumes that model.

## Before writing code

- **Find the rule IDs.** Every behavior comes from `docs/rules/`. If the rulebook and `specs/phase-2-engine.md` disagree, or neither settles a case, stop and ask the maintainer: numbered questions, each with a recommendation. Game decisions belong to them. If you must fill a gap to keep going, write the rule marked *(proposed)* and list it in `docs/rules/12-open-points.md`, following the `shardbound-rules` skill.
- **Check the slice.** `specs/phase-2-engine.md` §17 says what belongs where. §8 says how each rule is implemented, and §6.2 the canonical orders. Do not pull in work from a later slice without asking.

## The workflow

1. **Write the failing rule test first**, in the test class of the rulebook section (`CombatRulesTest` for section 7, `EffectRulesTest` for 8…). Its display name starts with the rule ID: `@DisplayName("8.10 — a frozen unit cannot intercept")`. Assert on the final state **and** on the trace (`trace(result)`), rule IDs included. Use real cards when one exercises the rule, and a `test.*` card in `testing.TestCards` otherwise.
2. **Implement it where the rule lives** (see the package map below). Most changes touch one small class, plus a `case` in a dispatching `switch`.
3. **Unlock the cards** that the change makes playable, in `rules.play.EngineSupport`, and update the expected list in `EngineSupportTest`. Then 1,000 random games exercise your code.
4. **Run `./mvnw verify`** from `engine/`. The random games check the invariants after every step, and the determinism, hidden-information and JSON tests catch most side effects of a change.
5. **Keep the docs in sync**, and **commit** following `CLAUDE.md` (see the checklist at the end).

## Where code goes (`rules` sub-packages)

| Package | Rulebook area | Typical classes |
|---|---|---|
| `rules.game` | the loop, the state check (1.2, 1.3, 1.6, 6.6, 9.8) | `Game`, `Resolver`, `StepRunner`, `StateCheck` |
| `rules.setup` | 5.1 | `GameFactory`, `Mulligans` |
| `rules.turn` | 4, 5.2–5.4, draws (1.4, 3.3) | `TurnStart`, `TurnEnd`, `MainPhase`, `CardDraws` |
| `rules.play` | 6.3, costs (6.8), legal plays | `CardPlay`, `Costs`, `PlayOptions`, `ChoiceSlots`, `EngineSupport` |
| `rules.combat` | 7 | `AttackSequence`, `AttackOptions`, `Interceptors` |
| `rules.effect` | 8, 10 | one `XxxEffect` per effect, `EffectResolution`, `Targets`, `TargetOptions` |
| `rules.trigger` | 9 | `Triggers`, `TriggerOrder`, `Abilities`, `AbilityTargets` |
| `rules.board` | arriving on and leaving the board (3.4, 3.6, 6.5) | `Arrivals`, `Departures`, `BoardSpace` |

A keyword (section 11) usually touches several of these. Put each part where its rule lives, and name the keyword in the comment: "11.3.4: a protected unit at 0 defense is doomed".

## Design rules, and why they exist

- **Outside, immutable; inside, one mutable `Game`.** Rule classes change the game only through `Game` (`updatePlayer`, `updateUnit`, `push`, `raise`, `ask`, `emit`, `end`) and through the domain methods of the records (`unit.damaged(3)`, `player.addToHand(card)`, `shards.pay(cost)`). States must stay immutable: MCTS and scenarios branch from them.
- **Steps are data.** A new `Step` is a record of plain values (ids, card ids, indexes, small records): no lambdas, no services. A paused game must survive `GameJson` and resume on another server instance. `GameJson` picks up new records of sealed types by itself. Avoid maps with record keys and non-record classes in the state.
- **Behavior lives in rule classes, never on records.** States, steps, actions and events are data; `StepRunner` and `EffectResolution` dispatch them to rule classes with `switch`es over sealed types. Do not give steps a `run()`/`resume()` method or an interface: steps must serialize, and `resolution` must not depend on `rules`.
- **Costs only through `rules.play.Costs`.** `toPlay` for cards (6.8), `toAttack` for attack abilities (Overcharge never applies to them, 11.4.5). Legal actions, payment and button labels all call them, so they cannot disagree.
- **One atomic effect per step.** The state check runs after every step: that is what makes "deal 4 to all enemy units" kill simultaneously, and keeps deaths and triggers in order.
- **Triggered abilities go through `Triggers.raise`.** Never resolve one inline: the buffer, `TriggerOrder` and the queue implement 9.8, 9.10 and 9.11. The only exception is "Attack" abilities, which `AttackSequence` starts right away (9.9).
- **Ask only for real choices.** No option: the effect does nothing (10.3). One option: take it automatically. Two or more: `game.pauseAndAsk(step, …)`, with the actions in canonical order. `MAIN` is the exception: it is always asked, with `game.ask`. The `Resolver` only ever `run`s steps; `GameEngine.apply` `resume`s the paused one with the answer.
- **Randomness only through `game.rng()`**, and always in a fixed order. Never use `Math.random`, `HashMap` iteration order or the system time: determinism is tested, and replays and evaluations rely on it.
- **Every visible change emits an event with its rule IDs.** This includes changes that "did nothing" for a stated reason (`SummonFailed`, 0-damage hits), as the maintainer prefers: the client animates them, and the trace explains them. A private event overrides `visibility()` and `redacted()`.
- **Hidden information never leaks.** Views come from `PlayerViews`, and events reach players through `Redaction`. A new private event, or a new field showing a hand or a deck, needs a redaction test.
- **Canonical orders are API.** Clients and seeded bots answer with indexes. Changing an order, or inserting actions, changes every recorded game: do it only on purpose, and say so in the commit.

## Code style (the maintainer's)

- Clean code: SRP, GRASP, SOLID. Small `final` classes with one job, grouped by rulebook area. Each rule procedure is a static method that takes the `Game`, and the variation points are sealed types with an exhaustive `switch`. The compiler then points at every place a new effect, step or event must be handled.
- Intention-revealing names over comments. Comments are short, simple sentences that state the rule and cite its ID: `/** 7.5: the defender's other units that are not frozen and have not intercepted this turn. */`. Never re-explain the whole method.
- Records with domain methods. `Unit` and `PlayerState` change through a private `Draft`, so each method names only the fields it touches.
- Booleans and their setters say who did what: `hasAttackedThisTurn` and `markHasAttacked`, not `attackedThisTurn` (which reads as "was attacked").
- Imports, never fully qualified names. English everywhere. No code for a later slice "just in case". Small preparations the maintainer asked for are fine, such as `UnitCard.size()`.
- Ports are interfaces, and implementations add a suffix: `GameRepository` → `GameRepositoryInMemory`, later `GameRepositoryDatabase`.
- Every package has a `package-info.java` saying in one or two sentences what it is for. A new package gets one, and a package whose role changes gets its description updated.

## Recipes

Read `references/recipes.md` for the step-by-step version of each:

- add an effect, or finish one that only exists in the card format;
- add a trigger;
- add a keyword;
- add an event;
- add a step, or a decision in the middle of a resolution;
- add a decision kind or an action type;
- change how an existing rule behaves;
- extend the scenario service for a test.

## Testing conventions

- **Display names start with the rule ID.** The rulebook coverage test (slice 4) reads them, and fails when a rule of sections 1 to 11 has no test.
- **Assert state and trace.** `trace(result)` gives `"UnitDamaged[8.1]"` lines. Prefer `containsSubsequence` to check order without listing every event.
- **Mind the scenario defaults.** The builder starts at turn 3, with P1 active and first player, 0 Shards and empty decks, and units placed as if they arrived last turn.
  - An empty deck means fatigue at the next draw: give the next player a `deck(...)` when a turn passes.
  - "0 actions match" in an error usually means too few Shards, or a unit that cannot attack.
- **Name ids by card.** Instance ids and arrival order follow the builder's call order, so a test can rely on them, but `Pick.unit(card)` reads better than raw ids.
- **Full-game tests guard everything else**: `RandomGamesTest` (invariants, every action applicable), `DeterminismTest`, `HiddenInformationTest`, `GameJsonTest`. If one fails after your change, print a described log of that seed (see below) before touching the test.

## Debugging

```java
var game = GameDriver.play(engine, new GameSetup(ember, root, seed), new RandomBot(a), new RandomBot(b));
EventDescriber text = new EventDescriber(catalog);
engine.eventsFor(game.events(), PlayerId.P1).forEach(e -> System.out.println(e.rules() + " " + text.describe(e, PlayerId.P1)));
```

Put it in a throwaway test, read the log, then delete the test. When a random-game test fails, find its seed (add it to the assertion's description if needed), then replay that game with the same bot seeds: it replays exactly.

## Before you commit

- [ ] Rule tests named by rule ID; `./mvnw verify` green from `engine/`.
- [ ] `EngineSupport` and `EngineSupportTest` updated if cards became playable.
- [ ] `specs/phase-2-engine.md` matches what you built (§8 notes, §6.4 event table, §13 if the protocol moved).
- [ ] Rulebook untouched, unless the maintainer decided a rule. In that case: new IDs at the end of the section, `12-open-points.md` updated, and the change committed as `docs(rules)`.
- [ ] End of a slice: the `docs/design.md` changelog, `CLAUDE.md` status and commands, the `shardbound-project` skill status, and this skill or `shardbound-engine` if a convention or the model changed.
- [ ] Conventional commit (`feat(engine): …`), imperative, a body that explains why, tests in the same commit, `Co-Authored-By` trailer. One branch and one PR per slice; never force-push.
