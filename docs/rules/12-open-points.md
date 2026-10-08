# 12. Open points and proposed rules

*Part of the [Shardbound rulebook](README.md).*

## Still open

- **"After attack" trigger.** The maintainer expects attack abilities to get "before attack" and "after attack" triggers (for example "after attack: sacrifice a unit": the unit attacks, then dies). Today's "Attack" trigger (9.9) is the "before attack" one, and 7.10 already settles a unit that leaves the board before its attack. When to add "after attack" is still open.
- **Unit size.** Idea from the maintainer: a `size` for units (1 by default) so that a unit could take 2 places on the board and count as 2 sacrifices. Not in the card format yet; the engine already counts board places and sacrifices through unit sizes, all equal to 1 for now.
- **Instance ids and decklists.** The engine numbers each deck in decklist order before shuffling it, so once cards are revealed, their ids hint at the opponent's decklist: after "Root Sentinel #36", #37 is a second Root Sentinel. Ids never reveal a hand or the next draw, and with the starter decks they say nothing a player does not already know. No engine change for now (2026-10-08): the bots' determinization reads nothing into ids. To settle before players build their own decks (phase 7).

The number scale for unit defense and damage is a card design guideline rather than a rule; it lives in [`content/cards/README.md`](../../content/cards/README.md).

## Proposed, awaiting validation

None at the moment.

## Settled for the engine (2026-10-05)

Gaps found while specifying the engine (`specs/phase-2-engine.md`), settled with the maintainer:

| Rule | Decision |
|---|---|
| 1.6 | The game ends as soon as a player drops to 0 HP; whatever has not resolved never resolves. |
| 3.8 | A unit with a sacrifice cost can be played on a full board if the sacrifice really frees a place. |
| 5.5.2 | *Reworded*: intercepting is no longer described as the only thing a player does during the opponent's turn; they also make the choices that rules and effects ask of them. |
| 6.8 | A card's cost is the sum of its printed cost, cost auras and −2 for Overcharge, floored at 0 once. |
| 6.9 | A unit's defense never goes below 0. |
| 7.9 | An attack whose target left the board still takes place; the effects on the target do nothing. |
| 7.10 | An attack whose attacker left the board does not take place; its cost stays paid. |
| 8.10 | *Reworded*: Freeze lasts for the rest of the current turn and the next turn; the unit thaws at the start of the turn after. |
| 8.15 | Damage never goes below 0; a 0-damage effect still hits. |
| 8.16 | A sacrifice is never partial: a card or an attack that asks for more sacrifices than its controller can make cannot be played or used. |
| 8.17 | A temporary defense malus that expires gives back what it took (max and current defense). |
| 8.18 | Attack damage modifications apply to every damage effect of the unit's attack abilities, not to its other abilities nor its echoes. |
| 8.19 | "Until end of turn" means the end of the current turn, whoever's turn it is. |
| 8.20 | Recall into a full hand does nothing. |
| 8.21 | A stat aura's defense malus that stops applying gives back what it took, like 8.17. |
| 8.22 | No sacrifice, no ability: a triggered ability or an echo whose sacrifices cannot be made does nothing at all. |
| 9.11 | An action or an ability resolves completely before the abilities it triggered. |
| 10.5 | A spell's targets are all chosen when it is played. |
| 10.6 | The targets of a triggered ability or an echo are all chosen when it starts resolving. |
| 11.1.10 | Echo rounds negative values toward 0. |
| 11.2.7 | A Fracture card returning to a full hand goes to the graveyard and loses its progress. |

## Settled for the keywords (2026-10-08)

Gaps found while implementing the keywords (phase 2, slice 4), settled with the maintainer:

| Rule | Decision |
|---|---|
| 5.4.3 | *Reworded*: the abilities that destroying the doomed units triggers resolve before 5.4.4, within the turn. |
| 6.10 | Maluses can take a unit's max defense to 0 or below; its defense stays at 0 and no heal raises it above its max. An anchored unit stays doomed until a malus ends and gives back what it took, as 8.17 says: both defenses go up by Y. |
| 11.1.11 | An echo does not scale the count of a Sacrifice effect, which is the price of the effects after it; an amount scaled to 0 does nothing, damage excepted (8.15). |
| 11.2.8 | A Fracture card with a sacrifice cost pays it at each step. |
