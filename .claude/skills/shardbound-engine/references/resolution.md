# The resolution model, step by step

How `GameEngine.apply` turns one action into a new state and a rule trace. The code is in `engine/core/src/main/java/fr/daliush/shardbound/core/rules/`.

## Contents

1. The working copy (`rules.game.Game`)
2. The loop (`Resolver`)
3. The steps
4. Triggered abilities: raise, order, queue, start
5. The state check
6. Decisions in the middle of a resolution
7. Targets: chosen, then resolved
8. Canonical orders
9. A worked trace

## 1. The working copy

`GameState` is immutable, so a resolution works on `Game.of(state, catalog)`, a mutable copy. It holds:

- the turn, the active player and the two `PlayerState`s (immutable records, replaced through `updatePlayer(id, p -> …)` and `updateUnit(unit)`);
- the steps (a deque) and the trigger queue, copied from `state.resolution()`;
- the `raised` buffer: abilities that triggered during the current step (not part of the state, always flushed by the state check);
- the pending decision, the result, the id and arrival counters, and a `SplitMix64` built from `state.rng()`;
- the events emitted so far.

`game.toState()` freezes everything back. Immutability is an API guarantee, not an implementation constraint (spec §5).

## 2. The loop

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
- A step with `waitsForTriggers()` (only `FinishTurn` today) lets the queue drain first: "Turn end" abilities resolve before 5.4.2 to 5.4.4 (5.4.1).

`GameEngine.apply` feeds the action first: a `MULLIGAN` answer goes to `Mulligans.answer`, a `MAIN` answer to `MainPhase.answer` (which pushes a step), and anything else to `StepRunner.resume` on the front step (section 6). `newGame` builds the state in `GameFactory` and stops at the first mulligan without running the loop.

## 3. The steps

Steps are records in `resolution.Step`: plain data, so a paused game can be stored and resumed anywhere. `StepRunner.run` sends each one to its rule class with an exhaustive `switch`.

| Step | Run by | What it does |
|---|---|---|
| `StartTurn(player)` | `turn.TurnStart` | turn + 1; `TurnStarted`; clears "attacked / intercepted this turn" on every unit (7.2, 7.5 mean every turn); refills Shards (4.1, 4.2); draws, except on the first player's very first turn (5.2.3); raises "Turn start" abilities of the player's units and relics, by arrival (5.2.4, 9.5) |
| `TriggerTurnEnd(player)` | `turn.TurnEnd` | raises "Turn end" abilities (5.4.1, 9.6), pushes `FinishTurn` |
| `FinishTurn(player)` | `turn.TurnEnd` | waits for the queue; empties Shards (5.4.4); `TurnEnded`; after turn 100 a draw (1.5), otherwise pushes `StartTurn(opponent)` |
| `ResolvePlay(player, play)` | `play.CardPlay` | spreads the spell's chosen targets over its effects (before anything changes, 10.5); pays the cost (6.8); `CardPlayed`; a unit or relic arrives (`board.Arrivals`, raises "Arrival"); a spell pushes `ResolveEffects` then `FinishSpell` |
| `FinishSpell(spell)` | `play.CardPlay` | the spell goes to its owner's graveyard; `SpellResolved` |
| `ResolveAttack(…, phase)` | `combat.AttackSequence` | `DECLARE`: pays, marks the attacker, `AttackDeclared`, puts the "Attack" abilities (9.9) in front of the `INTERCEPT` phase. `INTERCEPT`: attacker gone → `AttackCancelled` (7.10); target unit still there with eligible interceptors → asks the defender; otherwise `EFFECTS`. `EFFECTS`: attacker gone → cancelled; otherwise pushes `ResolveEffects` with the attack's target |
| `ChooseTargets(source, chosen)` | `trigger.AbilityTargets` | a triggered ability picks all its targets first (10.6): no option → none, one → automatic, two or more → asks the controller |
| `ResolveEffects(source, next, chosen)` | `effect.EffectResolution` | pushes itself for the next effect, then applies one effect (`DamageEffect`, `DestroyEffect`…). One atomic effect per step, so the state check runs between effects |

An `EffectSource` tells an effect where it comes from: an `EffectList` address (`SpellEffects`, `AttackEffects`, `AbilityEffects`; the state stores the address, not the effects), the card instance (for `self`), the controller (for `you`, `opponent`) and, inside an attack, the attack's target.

## 4. Triggered abilities

1. **Raise.** Rules call `Triggers.raise(game, card, controller, arrivalSeq, trigger)`: an arrival, a death (`board.Departures` raises Death then Departure), the start or end of a turn. Each matching ability of the card goes to the `raised` buffer as a `QueuedTrigger`. The trigger keeps the controller and the arrival sequence, so a card that just left the board keeps its place (9.8).
2. **Order.** At the end of the step, `StateCheck` sorts the buffer with `TriggerOrder` (9.8, 9.10): active player's first, then the earliest arrival, then Death before Departure for one card, then printed order. It appends the result to the queue.
3. **Queue.** The queue is FIFO, so abilities raised while others resolve go after those already waiting (9.8).
4. **Start.** `Abilities.start` emits `AbilityTriggered` with the trigger's rule ID (9.2 to 9.6, 9.9) and pushes a `ChooseTargets` step.

"Attack" abilities are the one exception: `AttackSequence` starts them right away with `Abilities.begin`, so they resolve before the intercept decision (9.9). What they cause (deaths, other triggers) still goes through the buffer and the queue.

## 5. The state check

`StateCheck.run` runs after every step:

1. Every unit at 0 defense is destroyed. They are destroyed together (6.6), and the check repeats until no unit is at 0. A unit's defense never goes below 0 (6.9).
2. The raised abilities are sorted and queued (section 4).
3. A player at 0 HP or less ends the game: `Win` for the other player (1.2), `Draw(DOUBLE_KO)` if both are down (1.3). `Game.end` drops every step, queued ability and pending decision (1.6) and emits `GameEnded`.

## 6. Decisions in the middle of a resolution

When a step needs a choice:

1. With no option, the effect does nothing (10.3). With one, it is taken automatically. Only with two or more does the engine ask.
2. The handler pushes a step back on the front. It is either itself, or an updated copy carrying what it has learned so far, such as the targets already chosen. Then it calls `game.ask(player, kind, actions)`, with the actions in canonical order. The loop stops, because a decision is pending.
3. On the next `apply`, `GameEngine` pops that front step and calls `StepRunner.resume(game, step, answer)`. The handler records the answer and pushes the next step, and the loop goes on.

Since the paused step is data inside the state, a game can stop here, be written to JSON, and resume on another server instance. Today two steps pause: `ResolveAttack` (`INTERCEPT`) and `ChooseTargets` (`CHOOSE_TARGET`).

## 7. Targets

- **Chosen when the spell is played** (10.5). `play.ChoiceSlots` lists the slots (effects with a chosen target that has at least one valid option) and their combinations for `MAIN`. When the card resolves, it spreads `PlayCard.targets` back over the effects.
- **Chosen when the ability starts resolving** (10.6): `ChooseTargets`.
- **Resolved when the effect applies.** `effect.Targets.resolve` keeps a chosen target only if it is still valid (10.3, 10.5). It also handles the rest: it draws random units with the game RNG, `self` is the source if still on the board, `attack_target` is the attack's target if still on the board (7.9), groups are taken in arrival order, and players are relative to the controller.
- **Options**, in canonical order: `effect.TargetOptions` for effect targets, `combat.AttackOptions.targets` for attacks (7.3), `combat.Interceptors.eligible` for intercepts (7.5).

## 8. Canonical orders (spec §6.2)

- `MAIN`: `PlayOptions` (cards in hand order, then target combinations), then `AttackOptions` (attackers by arrival, attack index, targets), then `EndTurn`.
- `INTERCEPT`: `DeclineIntercept`, then `Intercept` by interceptor arrival.
- Targets: units by arrival, then relics by arrival, then players (the deciding player first). Combinations are lexicographic.

Clients and seeded bots answer with an index, so these orders are part of the API: changing one changes every recorded game.

## 9. A worked trace

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
