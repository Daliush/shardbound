---
name: shardbound-rules
description: How Shardbound's game rules are organized and how to work with them safely. Use this whenever you answer or reason about a Shardbound rules question (Shards, combat, intercept, fatigue, doomed units, Echo, Fracture, Anchor, Overcharge, Link…), write or edit a rule or a ruling, or implement or test rules in the engine — even for a quick "what happens if…" about a game situation.
---

# Shardbound rules

## Where the rules live

The rulebook in `docs/rules/` is the written source of truth: one file per section, indexed by `docs/rules/README.md`. Read the relevant files before answering or changing anything: this skill explains how the rulebook works, it does not replace it, and the rules still move.

Once the engine exists, it becomes the executable specification and the rulebook is checked against it by tests. If the two disagree, that is a bug to raise, not something to paper over.

`docs/design.md` §2 holds a short summary for readers of the design doc. When they disagree, the rulebook wins.

## Rulebook map

The rule ID tells you which file to open: rule `7.5` is in `07-combat.md`, rule `11.3.2` is in `11-3-anchor.md`.

| File in `docs/rules/` | Topic |
|---|---|
| `00-vocabulary.md` | Vocabulary |
| `01-the-game.md` | HP, victory, fatigue, turn limit |
| `02-deck-building.md` | Deck building |
| `03-zones.md` | Zones (deck, hand, board, graveyard) and hidden information |
| `04-shards.md` | Shards |
| `05-game-sequence.md` | Setup, turn start, main phase, turn end, opponent's turn |
| `06-cards.md` | Cards and card types |
| `07-combat.md` | Combat and intercept |
| `08-effects.md` | Effects (closed list) |
| `09-triggers.md` | Triggers (closed list) and order of simultaneous effects (9.8) |
| `10-targets.md` | Targets (closed list) |
| `11-1-echo.md` … `11-5-link.md` | One file per keyword: Echo, Fracture, Anchor, Overcharge, Link |
| `12-open-points.md` | Open points and proposed rules |

When adding a section or a keyword, create its own file following the same naming and add it to the table in `docs/rules/README.md`.

## Rule identifiers are an API

Every rule has a stable ID such as `11.3.2`. The Arbiter cites these IDs, the engine writes them into its resolution trace, rulings and eval datasets reference them, and LoRA adapters are trained on answers that cite them. Renumbering would silently corrupt all of that. So:

- never renumber or reuse an ID;
- add new rules at the end of their section (`11.3.7`) or as a new section;
- to remove a rule, keep its ID and replace the text with `*(removed in vX: reason)*`.

## Status markers

- `*(proposed)*`: a rule written to keep the book complete, not yet validated by the maintainer.
- `12-open-points.md` lists every proposed rule and every point still open. Update it in the same change as the rule itself.

## Vocabulary that must stay precise

Ambiguous words are exactly what makes LLMs, and therefore the evaluation, fail. The rulebook gives each term one meaning:

- **HP** is for players only. Units have **defense** (current and max).
- **Destroy** (board → graveyard), **return to hand** (board → hand) and **discard** (hand → graveyard) are three different actions. "Discard" never applies to the board.
- **Die** means destroyed or sacrificed. Returning to hand is not dying, hence two triggers: **Death** (9.3) and **Departure** (9.4).
- **Ability** is what a card does. **Attack ability** is a unit's paid attack. Do not call abilities "attacks".
- **Controller** is whose board the card is on; **owner** is whose deck it came from.

When the maintainer uses a term loosely in conversation, map it to the precise term in the files.

## Answering a rules question

Use the Arbiter format. It is also the format of the eval answer keys, so practising it keeps everything consistent:

```
Rules cited: <rule IDs>
Resolution:
1. ...
2. ...
Verdict: ...
```

- Resolve step by step, in the order the rules impose: turn start and turn end follow 5.2 and 5.4; simultaneous triggers follow 9.8 (active player first, then the earliest arrival on the board first, and triggers raised during resolution queue after those already waiting).
- Cite the exact IDs you relied on.
- If the answer depends on a *(proposed)* rule or an open point, say so and answer under the proposed rule. Do not invent rules to fill a gap.

## Changing the rules

Game design decisions belong to the maintainer. They like to go point by point and push back when a proposal does not fit their vision. So:

1. Discuss before writing. Present the open questions with a recommendation, numbered so they can be answered by number.
2. If a gap must be filled to keep the book complete, write the rule, mark it *(proposed)* and tell the maintainer.
3. In the same change, update the rule's file, `12-open-points.md`, and `docs/design.md` §2 if the summary is affected.
4. Turn the edge case into a ruling in `docs/rulings/`. Each ruling becomes documentation, a JUnit test and an eval question.
5. Once the engine and the evals exist, update the engine tests and regenerate the eval answer keys.

## Design decisions and why (don't undo them by accident)

- **Combat is Pokémon-like, not Magic-like.** Attacking costs Shards and uses one of the unit's one or two attack abilities, once per turn. Playing a card costs Shards too: the maintainer first assumed cards were free and only abilities cost Shards, so restate this when it matters. Attacks target units; the player can only be attacked once they have no unit left. The defender intercepts (redirects) rather than blocks, and there is no retaliation. A Magic-style model (free attacks, attack/defense stats, blocking with retaliation) was proposed and rejected.
- **Nothing is played during the opponent's turn** except intercepting. This keeps the engine and the player protocol simple.
- **Fatigue and the 50-turn limit** guarantee every game ends. Simulations and MCTS rollouts depend on it, since an empty deck alone does not lose.
- **Anchor lasts through the opponent's turn.** Without a response window it would otherwise be useless. A doomed unit gets one last turn.
- **Echo is a passive of an attack ability**, not of the unit. It triggers on death only, so Tide can answer an Echo 300 attacker by returning it to hand.
- **Fracture steps are public but not displayed**: the opponent has to remember. Bots receive the public event history.
- **Ordering is deterministic everywhere** (9.8). An interaction must have exactly one correct answer, or the engine cannot produce an answer key.
- **Keyword names avoid close collisions** with known games ("Overcharge", not "Overload"), so a base LLM does not import a prior.

## Edge cases worth turning into rulings

- An anchored unit at 0 defense healed by a "Turn end" relic before the doom check (5.4.1 runs before 5.4.3).
- An Echo 300 unit returned to hand: no echo (11.1.5). Sacrificed: echo (9.3).
- An echo chain after a board wipe: ordered by 9.8, new echoes queue after the waiting ones.
- A Link between an allied and an enemy unit taking an odd amount of damage (11.5.2).
- An overcharged 1-cost card: costs 0 and still locks 2 Shards (11.4.1, 11.4.2).
- A Fracture card discarded between two steps: progress is lost (11.2.6).
- A unit returned to a full hand: graveyard, no echo (8.8).
- A temporary defense bonus expiring on a damaged unit (8.5 has a worked example).
- A unit dies when both of its attack abilities have Echo: both replay, in the order its owner chooses (11.1.8).
- A unit with an Echo attack and a "Death" ability dies: Echo first, then Death (9.10).
- A buffed unit dies with Echo: the echo uses the printed values, buffs are lost (11.1.9).
- A no-target attack ("give all your units +5/+0"): cannot be intercepted but still triggers "Attack" abilities (7.8).
- An "Attack" ability that freezes the enemy unit about to intercept: it fires just before the intercept decision (9.9).
