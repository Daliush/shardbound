# The resolution model, step by step

How `GameEngine.apply` turns one action into a new state and a rule trace. The code is in `engine/core/src/main/java/fr/daliush/shardbound/core/rules/`.

## Contents

1. Who does what: `GameEngine` and `Resolver`
2. The working copy (`rules.game.Game`)
3. The loop (`Resolver`)
4. The steps
5. Triggered abilities: raise, order, queue, start
6. The state check
7. Decisions in the middle of a resolution
8. Targets: chosen, then resolved
9. Canonical orders
10. A worked trace

## 1. Who does what

| | `GameEngine` | `Resolver` |
|---|---|---|
| Role | The front desk: the only class callers use | The motor: a loop inside one `apply` call |
| Does | Checks the action is listed, makes the working copy, hands the answer to the right place (new work, or the paused step), starts the loop, freezes the result | Runs pending work until a decision is pending or the game is over, with a state check after each pass |
| Sees actions | Yes, one per call | Never |

## 2. The working copy

`GameState` is immutable, so a resolution works on `Game.of(state, catalog)`, a mutable copy. It holds:

- the turn, the active player and the two `PlayerState`s (immutable records, replaced through `updatePlayer(id, p -> …)` and `updateUnit(unit)`);
- the steps (a deque) and the trigger queue, copied from `state.resolution()`;
- the `raised` buffer: abilities that triggered during the current step (not part of the state, always flushed by the state check);
- the pending decision, the result, the id and arrival counters, and a `SplitMix64` built from `state.rng()`;
- the events emitted so far.

`game.toState()` freezes everything back. Immutability is an API guarantee, not an implementation constraint (spec §5).

## 3. The loop

`Resolver.run(game)`:

```
while the game is not over and no decision is pending:
    next = the front step, if any
    if (no next step, or next.waitsForTriggers()) and the queue is not empty:
        start the first queued ability            (Abilities.start)
    else if there is a next step:
        pop it and run it                         (StepRunner.run)
    else:
        ask MAIN for the active player            (MainPhase.ask)
    StateCheck.run(game)
```

Two consequences, both rules:

- Steps always run before the queue, so **an action resolves completely before the abilities it triggered** (9.11).
- A step with `waitsForTriggers()` (`FinishTurn` and `PassTurn`) lets the queue drain first: "Turn end" abilities resolve before 5.4.2 (5.4.1), and the Death abilities of the units 5.4.3 destroys resolve before the turn passes.

`game.push(a, b, c)` puts the steps at the front **in the order given**: `a` runs first, then `b`, then `c`, then whatever was already there. Steps run front first; the trigger queue is a separate FIFO.

`GameEngine.apply` feeds the action first: a `MULLIGAN` answer goes to `Mulligans.answer`, a `MAIN` answer to `MainPhase.answer` (which pushes a step), and anything else to `StepRunner.resume` on the front step (section 7). `newGame` builds the state in `GameFactory` and stops at the first mulligan without running the loop.

## 4. The steps

Steps are records in `resolution.Step`: plain data, so a paused game can be stored and resumed anywhere. `StepRunner.run` sends each one to its rule class with an exhaustive `switch`.

| Step | Run by | What it does |
|---|---|---|
| `StartTurn(player)` | `turn.TurnStart` | turn + 1; `TurnStarted`; thaws the units whose freeze has ended (8.10); ends the Anchor protection of the player's units (5.2.1); clears "attacked / intercepted this turn" on every unit (7.2, 7.5 mean every turn); refills Shards (4.1, 4.2); draws, except on the first player's very first turn (5.2.3); raises "Turn start" abilities of the player's units and relics, by arrival (5.2.4, 9.5) |
| `TriggerTurnEnd(player)` | `turn.TurnEnd` | raises "Turn end" abilities (5.4.1, 9.6), pushes `FinishTurn` |
| `FinishTurn(player)` | `turn.TurnEnd` | waits for the queue; ends every "until end of turn" modifier (5.4.2, 8.19); destroys the player's doomed units still at 0 whose protection has ended (5.4.3); pushes `PassTurn` |
| `PassTurn(player)` | `turn.TurnEnd` | waits for the queue; empties Shards (5.4.4); `TurnEnded`; after turn 100 a draw (1.5), otherwise pushes `StartTurn(opponent)` |
| `ResolvePlay(player, play)` | `play.CardPlay` | spreads the spell's chosen targets over its effects, those of its next step for a Fracture card (before anything changes, 10.5); pays the cost (6.8) and the sacrifice cost; locks 2 Shards if overcharged (11.4.2); `CardPlayed`; a unit or relic arrives (`board.Arrivals`, raises "Arrival"); a spell pushes `ResolveEffects` then `FinishSpell` |
| `FinishSpell(spell)` | `play.CardPlay` | `spell` is the hand card played; the spell goes to its owner's graveyard (`SpellResolved`), or a Fracture card before its last step returns to hand one step further (`FractureAdvanced`; full hand: graveyard, 11.2.7) |
| `ResolveAttack(…, phase)` | `combat.AttackSequence` | `DECLARE`: pays, marks the attacker, `AttackDeclared`, puts the "Attack" abilities (9.9) in front of the `INTERCEPT` phase. `INTERCEPT`: attacker gone → `AttackCancelled` (7.10); target unit still there with eligible interceptors → asks the defender; otherwise `EFFECTS`. `EFFECTS`: attacker gone → cancelled; otherwise pushes `ResolveEffects` with the attack's target |
| `StartEchoes(unit, controller, attacks)` | `trigger.Echoes` | with two Echo attacks, asks the owner their order (`CHOOSE_ORDER`, 11.1.8) and pushes one `StartEchoes` per attack; with one, emits `EchoTriggered` and pushes its `ChooseTargets` |
| `ChooseTargets(source, chosen)` | `trigger.AbilityTargets` | first, an ability or echo that cannot make all its sacrifices does nothing (8.22); then an echo picks its new attack target with the rules of 7.3 (11.1.3); then it picks all its targets (10.6): no option → none, one → automatic, two or more → asks the controller |
| `ResolveEffects(source, next, chosen)` | `effect.EffectResolution` | pushes itself for the next effect, then applies one effect (`DamageEffect`, `ModifyEffect`…). One atomic effect per step, so the state check runs between effects. An effect that makes a player pick cards (a Sacrifice effect, a player's Discard) asks `CHOOSE_CARDS` with one option per combination, and the answer resumes the step; a sacrifice that can no longer be made stops the list (8.22) |

**A step with phases decides the next phase itself.** The loop knows nothing about phases: `ResolveAttack` in phase `DECLARE` ends by pushing itself back with `inPhase(INTERCEPT)`, and so on. `AttackSequence.run` only picks the code of the current phase.

**Behavior is not on the steps.** Steps are records of plain values, so they serialize with the state. The code lives in rule classes, and `StepRunner` sends each step to it with a `switch` over the sealed type. There is no `run()` or `resume()` method on a step, and no interface: that would tie the `resolution` package to every rules package and mix data with behavior.

An `EffectSource` tells an effect where it comes from: an `EffectList` address (`SpellEffects` with the Fracture step played, `AttackEffects`, `EchoEffects`, `AbilityEffects`; the state stores the address, not the effects), the card instance (for `self`), the controller (for `you`, `opponent`) and, inside an attack or an echo, the attack's target. Only `AttackEffects` is an attack: an echo gets no attack bonus (8.18).

## 5. Triggered abilities

1. **Raise.** Rules call `Triggers.raise(game, card, controller, arrivalSeq, trigger)`: an arrival, a death (`board.Departures` raises the echoes, then Death, then Departure), the start or end of a turn. Each matching ability of the card goes to the `raised` buffer as a `QueuedTrigger.TriggeredAbility`; a unit that dies with Echo attacks adds one `QueuedTrigger.Echoes` (`trigger.Echoes.raise`, 11.1.1). The entry keeps the controller and the arrival sequence, so a card that just left the board keeps its place (9.8).
2. **Order.** At the end of the step, `StateCheck` sorts the buffer with `TriggerOrder` (9.8, 9.10): active player's first, then the earliest arrival, then Echo, Death, Departure for one card, then printed order. It appends the result to the queue.
3. **Queue.** The queue is FIFO, so abilities raised while others resolve go after those already waiting (9.8): echo chains come naturally (11.1.6).
4. **Start.** `Abilities.start` emits `AbilityTriggered` with the trigger's rule ID (9.2 to 9.6, 9.9) and pushes a `ChooseTargets` step; an `Echoes` entry pushes a `StartEchoes` step instead.

"Attack" abilities are the one exception: `AttackSequence` starts them right away with `Abilities.begin`, so they resolve before the intercept decision (9.9). What they cause (deaths, other triggers) still goes through the buffer and the queue.

## 6. The state check

`StateCheck.run` runs after every step:

1. Stat auras are reconciled (`aura.StatAuras`, 8.14): each unit gets exactly what the aura cards on the board give it; a bonus that starts moves both defenses, one that stops ends like an expiring modifier.
2. A doomed unit back above 0 defense is no longer doomed (`DoomLifted`, 11.3.4), however it got there.
3. Every unit at 0 defense is destroyed, unless Anchor protects it: then it is doomed and stays (`UnitDoomed`, 11.3.4), and a doomed unit waits for 5.4.3. The others are destroyed together (6.6), the auras are reconciled again (a dead aura card stops applying), and the check repeats until no unit is left to destroy. A unit's defense never goes below 0 (6.9), even when maluses take its max below 0 (6.10).
4. The raised abilities are sorted and queued (section 5).
5. A player at 0 HP or less ends the game: `Win` for the other player (1.2), `Draw(DOUBLE_KO)` if both are down (1.3). `Game.end` drops every step, queued ability and pending decision (1.6) and emits `GameEnded`.

## 7. Decisions in the middle of a resolution

There are two kinds of decisions (`DecisionKind.pausesAStep()`):

- **Decisions that start new work**: `MULLIGAN` and `MAIN`. They are asked with `game.ask(...)`, when there is nothing to resume.
- **Decisions asked in the middle of a step**: `INTERCEPT` and every `CHOOSE_*`. The step waits for the answer.

When a step needs a choice:

1. With no option, the effect does nothing (10.3). With one, it is taken automatically. Only with two or more does the engine ask.
2. The handler computes the options in canonical order, then calls `game.pauseAndAsk(step, player, kind, actions)`. That puts the step back at the front of the pending work and creates the decision. The step is either the handler's own, or an updated copy carrying what it has learned so far, such as the targets already chosen. The loop stops, because a decision is pending.
3. On the next `apply`, `GameEngine` sees a decision that pauses a step. It pops the front step and calls `StepRunner.resume(game, step, answer)`; the step's rule class records the answer and pushes the next step, and the loop goes on.

The rule to remember: **the `Resolver` always calls `run`; only `GameEngine.apply` calls `resume`, at most once per call, right at the start, when the answered decision paused a step.** `run` means "do your work"; `resume` means "here is the answer to the question you asked". No flag marks a step as paused, and no `if (paused)` exists: the caller knows which situation it is in, and calls the matching method. `run` never sees an answer; `resume` never asks.

Decision kinds describe *the sort of answer* (a target, a set of cards, an order), not what it is for: the paused step knows that. A card that lets the opponent pick one of their units to freeze would use `CHOOSE_TARGET`, never a kind of its own.

Misuse fails loudly: `ask` refuses a decision that pauses a step, `pauseAndAsk` refuses `MAIN` and `MULLIGAN`, and each step checks that it is resumed in the right phase with the right kind of answer (`AttackSequence.resume`, `AbilityTargets.resume`).

Since the paused step is data inside the state, a game can stop here, be written to JSON, and resume on another server instance. Four steps pause: `ResolveAttack`, in its `INTERCEPT` phase, `StartEchoes` on a `CHOOSE_ORDER` decision, `ChooseTargets`, and `ResolveEffects` on a `CHOOSE_CARDS` decision. A spell paused this way is held by its resolution (its `FinishSpell` step), between hand and graveyard.

## 8. Targets

- **Chosen when the spell is played** (10.5). `play.ChoiceSlots` lists the slots (effects with a chosen target that has at least one valid option; Link fills two at once with a pair of units, 8.11) and their combinations for `MAIN`. When the card resolves, it spreads `PlayCard.targets` back over the effects.
- **Chosen when the ability starts resolving** (10.6): `ChooseTargets`.
- **Resolved when the effect applies.** `effect.Targets.resolve` keeps a chosen target only if it is still valid (10.3, 10.5). It also handles the rest: it draws random units with the game RNG, `self` is the source if still on the board, `attack_target` is the attack's target if still on the board (7.9), groups are taken in arrival order, and players are relative to the controller. The attack's target can be the opposing player: an effect that only takes units (`Targets.units`) skips them (10.3).
- **Options**, in canonical order: `effect.TargetOptions` for effect targets (Recall's are the unit cards of the controller's graveyard), `combat.AttackOptions.targets` for attacks (7.3), `combat.Interceptors.eligible` for intercepts (7.5), `effect.Sacrifices.options` for units to sacrifice.

## 9. Canonical orders (spec §6.2)

- `MAIN`: `PlayOptions` (cards in hand order, then target combinations), then `AttackOptions` (attackers by arrival, attack index, targets), then `EndTurn`.
- `INTERCEPT`: `DeclineIntercept`, then `Intercept` by interceptor arrival.
- Targets: units by arrival, then relics by arrival, then players (the deciding player first). Combinations are lexicographic.

Clients and seeded bots answer with an index, so these orders are part of the API: changing one changes every recorded game.

## 10. A worked trace

Scenario (`GameRulesTest`, rule 1.6): P1 at 2 HP casts Cinderfall ("Deal 4 damage to all enemy units"). P2 has two Cinderlings (#2, #3), whose Death ability deals 2 damage to the opponent.

```
CardPlayed[6.3]           ResolvePlay: cost paid, the spell pushes ResolveEffects + FinishSpell
UnitDamaged[8.1]          ResolveEffects: one atomic effect hits both units...
UnitDamaged[8.1]
UnitDestroyed[6.6]        ...then the state check destroys them together and queues their Death abilities
UnitDestroyed[6.6]
SpellResolved[6.3, 3.5]   FinishSpell: the action ends before its triggers (9.11)
AbilityTriggered[9.3]     the queue starts Cinderling #2, the earliest arrival (9.8)
PlayerDamaged[8.1]        P1 drops to 0 HP
GameEnded[1.2, 1.6]       the game ends at once: Cinderling #3's ability never resolves
```
