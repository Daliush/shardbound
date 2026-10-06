# Engine recipes

Step-by-step changes, with the files each one touches. Paths are relative to `engine/core/src/main/java/fr/daliush/shardbound/core/`, and tests to `engine/core/src/test/java/fr/daliush/shardbound/core/`.

## Contents

1. Add an effect
2. Add a trigger
3. Add a keyword
4. Add an event
5. Add a step, or a decision in the middle of a resolution
6. Add an action type or a decision kind
7. Change how an existing rule behaves
8. Extend the scenario service

## 1. Add an effect

### a. The effect already exists in the card format

Every effect of the closed list (rulebook section 8) is already parsed into `content/Effect.java`. Implementing one in the engine, for example Freeze (8.10):

1. **Test first**, in `rules/EffectRulesTest.java`: `@DisplayName("8.10 — …")`. If no playable real card has the effect, add a `test.*` card to `testing/TestCards.java` (JSON, same format as `content/cards/`).
2. **Handler**: `rules/effect/FreezeEffect.java`, a package-private `final` class with one method:
   ```java
   /** 8.10 Freeze: the rest of this turn and the next one; the unit thaws at the start of the turn after. */
   final class FreezeEffect {
       static void apply(Game game, Effect.Freeze freeze, EffectSource source, List<TargetRef> chosen) {
           for (TargetRef target : Targets.resolve(game, freeze.target(), source, chosen)) {
               // find the unit, change it through a Unit method, emit the event
           }
       }
   }
   ```
   Change units through a domain method of `state/Unit.java` (here `frozenThrough(turn)`), never through the canonical constructor.
3. **Event**: add the record to `event/GameEvent.java` (see recipe 4). The exhaustive `switch` in `text/EventDescriber.java` will not compile until you describe it.
4. **Dispatch**: add a `case` in `rules/effect/EffectResolution.apply`. That `switch` keeps a `default` branch while some effects are missing, so the compiler does not remind you: once the last effect lands, remove the `default` and let the `switch` become exhaustive.
5. **Targets**: plain targets work as they are. New kinds of choices need `rules/effect/TargetOptions` (the options), `rules/play/ChoiceSlots` (one entry per slot in `PlayCard.targets`) and `rules/effect/Targets.resolve` (what is still valid when the effect applies). Link has two slots (two different units); Recall has one, a card in the controller's graveyard.
6. **Rules around the effect**: put them where they live. Freeze's thaw is at the start of a turn (`rules/turn/TurnStart`), and the frozen checks already exist (`AttackOptions`, `Interceptors`, via `Unit.isFrozen`).
7. **Support**: add the effect class to `EngineSupport.IMPLEMENTED_EFFECTS`, and update the list in `rules/EngineSupportTest.java`.
8. **Spec**: the event table (§6.4), and the §8 note if what you built differs from it.

### b. A brand-new effect

That is a game design change: the closed list of section 8 grows. Ask the maintainer first. Then, in this order and in separate commits:

1. The rulebook (`docs(rules)`): the new rule at the end of section 8, `12-open-points.md`, and the `shardbound-card-authoring` skill's closed lists.
2. The content (`feat(content)`): `content/cards/card.schema.json`, `text-templates.json`, the cards README, and the content tests. Run `uv run pytest` in `content/`.
3. The engine: a new record in `content/Effect.java`, its case in `content/json/EffectParser.java`, then recipe 1a.

## 2. Add a trigger

Example: the "After attack" trigger, still an open point in `12-open-points.md`. It needs the maintainer's decision first.

1. Content and rulebook, as in 1b: the `Trigger` enum (schema and `content/Trigger.java`), its template label, a new rule in section 9, and parser constraints (which card types may have it) in `content/json/CardParser.java`.
2. **Where it fires**: call `Triggers.raise(game, card, controller, arrivalSeq, Trigger.X)` at the moment the rule names. For "After attack", that is the end of `rules/combat/AttackSequence`'s `EFFECTS` phase. Raised abilities then resolve after the attack completes, through the queue (9.11). Do not start them inline unless the rule says they resolve right away, as 9.9 does for "Attack".
3. `Triggers.ruleOf` (its rule ID in the trace), `TriggerOrder.rank` (only if the rulebook orders it against other triggers of one card), and `text/Wording.trigger` (its label in the log).
4. `EngineSupport`: allow the trigger. Tests: one per new rule, plus a 9.8 ordering case if it can fire together with others.

## 3. Add a keyword

A keyword is several small rules in different places. Example: Anchor (11.3):

| Rule | Where | Change |
|---|---|---|
| 11.3.1 protected on arrival | `rules/board/Arrivals` | set the flag when the card `has(Keyword.ANCHOR)` |
| 5.2.1 protection ends | `rules/turn/TurnStart` | first step of the start of turn; event `AnchorProtectionEnded` |
| 11.3.2, 11.3.3 cannot leave the board | `rules/board/Departures`, `DestroyEffect`, sacrifice and return to hand | prevent only that part; event `AnchorPrevented`; a sacrifice still counts as paid |
| 11.3.4 doomed at 0 defense | `rules/game/StateCheck` | doomed instead of destroyed; `UnitDoomed`; healed above 0 → `DoomLifted` |
| 11.3.5 destroyed at the end of turn | `rules/turn/TurnEnd.finish` | 5.4.3, after the "Turn end" abilities |

Then:

- **Support**: `EngineSupport.supports` refuses every keyword today (`card.keywords().isEmpty()`). When the first keyword lands, replace that with a set of implemented keywords, the same way as for effects. Do the same for Echo (on attacks) and Fracture (on spells).
- **Tests**: one per 11.3.x rule, in a `KeywordRulesTest` or one test class per keyword. The interactions in the `shardbound-rules` skill's "edge cases" make good extra tests.

## 4. Add an event

1. A record nested in `event/GameEvent.java`, in the right group (setup and turns, cards, combat, effects). Its components are what a reader needs without the state: `CardInstance` for cards, `PlayerId` for players, `EventTarget` for "a unit or a player". The last component is `List<String> rules`.
2. When the rule IDs never vary, add a convenience constructor that fills them in: `public Frozen(CardInstance unit, int throughTurn) { this(unit, throughTurn, List.of("8.10")); }`. When they vary (a draw caused by 5.2.3 or by 8.6), take them as a parameter.
3. Private to one player: override `visibility()` and `redacted()`, as `CardDrawn` does, and add an assertion to `simulation/HiddenInformationTest`.
4. Add the case to `text/EventDescriber` (the compiler insists), with a sentence from the viewer's point of view through `Wording` ("You…" / "Your opponent…").
5. Add the event to the §6.4 table of the spec. `GameJson` needs nothing: it finds every record of the sealed type.

## 5. Add a step, or a decision in the middle of a resolution

1. A record in `resolution/Step.java` carrying everything needed to resume: ids, card ids, indexes, the choices made so far, and a phase enum if the step has several phases (like `ResolveAttack`). Override `waitsForTriggers()` only if queued abilities must resolve before it, as for `FinishTurn`.
2. Add a `case` to `StepRunner.run` that calls the rule class's method.
3. If it may need a choice:
   ```java
   List<Action> options = …;              // canonical order
   if (options.isEmpty()) { …; return; }  // nothing to choose: the effect does nothing (10.3)
   if (options.size() == 1) { … }         // the only option, taken automatically
   game.pauseAndAsk(stepWithWhatWeKnowSoFar, player, DecisionKind.CHOOSE_TARGET, options);
   return;                                // the paused step is the memory of the choice
   ```
   Then add a `resume` method to the step's rule class and a `case` to `StepRunner.resume` that calls it. The method checks that the step is in the phase that asked and that the answer has the expected type, records the answer, and pushes the next step (or the updated step, if more choices follow, as in `AbilityTargets.resume`). A step with phases dispatches on its phase in `resume` as it does in `run` (see `AttackSequence`).
4. The deciding player can be the non-active one (5.5.2). The invariants check that only the active player ever gets `MAIN`, and that other decisions have at least two options.

## 6. Add an action type or a decision kind

- **Action**: a record in `action/Action.java` with value components only, since `apply` checks it by equality. Add a case to `text/ActionDescriber` for its button label, and put it in canonical order where it is listed. The API's `ActionView` gets a matching `type` in slice 2.
- **Decision kind**: `decision/DecisionKind.java`. Check `pausesAStep()`: a kind asked in the middle of a step goes through `pauseAndAsk`; one that starts new work goes through `ask` and needs a case in `GameEngine.startNewWork`. Update `testing/Invariants.checkDecision` if it has its own constraints, and add the API's `DecisionKind` string later.

## 7. Change how an existing rule behaves

1. The rulebook first, with the maintainer's decision, following the `shardbound-rules` skill. Keep the rule ID and reword its text, or add a rule at the end of the section. Update `12-open-points.md`. Commit as `docs(rules)`.
2. Update the test that carries the rule ID, keeping its display name's ID, and make it fail.
3. Change the code, then check the spec's §8 note.

## 8. Extend the scenario service

Tests should read like the rules. When a test needs something the builder cannot express:

- **A unit state**: add an option to `scenario/UnitSetup`, backed by a domain method of `Unit` (as `defense(…)` uses `Unit.withDefense`). Never build a `Unit` with its canonical constructor in the builder.
- **A player field**: a fluent method on `ScenarioBuilder` that takes the player and plain values, with cards named by id string.
- **A choice**: a factory in `scenario/Choices` that matches actions by value equality through `Choice.single`, so that "zero" and "several" matches keep their explicit error.
- **A target**: a factory in `scenario/Pick`.

The scenario service becomes a polished public API in slice 5, documented in `engine/core`'s README. Keep its names stable.
