# Shardbound — Rulebook (v0.1)

> **Status**: draft from the rules session of 2026-10-04.
> Rules marked *(proposed)* still await validation; [12 — Open points](12-open-points.md) lists them.
>
> **Identifiers**: every rule has a stable identifier (e.g. `11.3.2`). The Arbiter cites them, and the engine writes them into its resolution trace. Identifiers are global across files: `11.3.2` is always in the Anchor file. **Never** renumber an existing rule: add new ones, never shift old ones.

## Contents

| Section | File | Topic |
|---|---|---|
| 0 | [Vocabulary](00-vocabulary.md) | Precise meaning of every term |
| 1 | [The game](01-the-game.md) | HP, victory, fatigue, turn limit |
| 2 | [Deck building](02-deck-building.md) | 30 cards, one faction, 2 copies |
| 3 | [Zones](03-zones.md) | Deck, hand, board, graveyard, hidden information |
| 4 | [Shards](04-shards.md) | The resource |
| 5 | [Game sequence](05-game-sequence.md) | Setup, start / main / end of turn, opponent's turn |
| 6 | [Cards](06-cards.md) | Card types, playing a card, defense |
| 7 | [Combat](07-combat.md) | Attacking, targets, intercept |
| 8 | [Effects](08-effects.md) | Closed list of effects |
| 9 | [Triggers](09-triggers.md) | Closed list of triggers, order of simultaneous effects |
| 10 | [Targets](10-targets.md) | Closed list of targets |
| 11.1 | [Echo](11-1-echo.md) | Keyword |
| 11.2 | [Fracture](11-2-fracture.md) | Keyword |
| 11.3 | [Anchor](11-3-anchor.md) | Keyword |
| 11.4 | [Overcharge](11-4-overcharge.md) | Keyword |
| 11.5 | [Link](11-5-link.md) | Keyword |
| 12 | [Open points](12-open-points.md) | What is still open or only proposed |

## Adding to the rulebook

- A new rule goes at the end of its section, in the matching file (`11.3.7` goes at the end of the Anchor file).
- A new section gets the next free number, its own file named `NN-name.md`, and a row in the table above.
- A new keyword gets the next `11.N` number and its own `11-N-name.md` file.
