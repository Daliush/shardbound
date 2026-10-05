---
name: shardbound-card-authoring
description: How to design, write and review Shardbound cards — card anatomy, the closed lists of effects, triggers and targets, faction identities, where each keyword is allowed, deck constraints, balance watch-points and naming. Use this whenever you create, edit, review or balance a card or a set of cards, write card JSON or the card JSON Schema, generate cards with an agent, or discuss a card idea — even a single one.
---

# Writing Shardbound cards

## Before writing a card

Read these rulebook files in `docs/rules/`: `06-cards.md`, `07-combat.md`, the closed lists (`08-effects.md`, `09-triggers.md`, `10-targets.md`) and the keyword files `11-*.md`.

Cards are built only from those closed lists. That constraint keeps the JSON Schema finite, lets the engine implement each effect exactly once, and lets the eval datasets be generated automatically. If a card idea needs an effect, trigger or target that is not in the lists, it is a rules change: raise it with the maintainer instead of inventing it on the card.

## Anatomy

Every card has a name, a faction (Ember, Tide, Root or Neutral), a type and a cost in Shards.

- **Unit**: a max defense; one or two attack abilities (each with its own cost in Shards and effects; the unit uses only one of them per turn); optional other abilities; optional keywords. Most attacks hit a single target; an attack may also, or only, buff its own side ("give all your units +5/+0 until end of turn"), and then it has no target (7.8).
- **Spell**: one or more effects resolved on cast. A Fracture spell has 2 to 5 steps, each with its own cost (possibly 0) and its own effect.
- **Relic**: no defense; continuous (aura) or triggered abilities. It cannot be attacked.

An ability is always *trigger → effect(s) → target*. The closed lists, by name only (the rulebook is authoritative; if this list ever disagrees with it, trust the rulebook and fix this skill):

- **Effects (§8)**: Deal X damage, Destroy, Sacrifice, Heal X, Modify ±X/±Y, Draw X, Discard X, Return to hand, Summon, Freeze, Link, Gain Shards, Recall, Aura.
- **Triggers (§9)**: Cast, Arrival, Death, Departure, Turn start, Turn end, Continuous, Attack.
- **Targets (§10)**: one unit (allied, enemy or either), one player, all units (allied, enemy or all), a random unit, the card itself.

## Where each mechanic may appear

| Mechanic | Allowed on | Why |
|---|---|---|
| Echo X | On an attack ability (so units only), not on the unit itself | Only the attack carrying Echo is replayed, with its own X (11.1.1, 11.1.8). In the card JSON, Echo belongs to the attack ability |
| Fracture N | Spells only, N from 2 to 5. Fracture 5 on one or two cards at most | High risk, high reward; too many of them turns the game into step counting |
| Link | Neutral spells only | Maintainer decision |
| Recall | Ember only, on a few expensive cards | Prevents an Echo 300 + Sacrifice + Recall loop |
| Sacrifice | Ember | Faction identity |
| Anchor | Root (intended, not locked) | Faction identity; keeps it away from Ember's Sacrifice |
| Overcharge | Not assigned yet | Open |
| Attack ability | One or two per unit; a single target, or no target for self-buff attacks | Only targeted attacks can be intercepted (7.5, 7.8) |

## Faction identities

The maintainer set the core identities, **Ember = attack, Tide = buffs and debuffs, Root = summoning**, and approved the effect mapping below (2026-10-05).

| Faction | Identity | Typical effects |
|---|---|---|
| Ember | Attack | High-damage attacks, direct damage to the player through spells (spells can hit the player even when they have units), Sacrifice, Recall, Echo, Overcharge, temporary attack bonuses |
| Tide | Buffs and debuffs | Permanent Modify (both directions), stat auras, Freeze, Return to hand (the clean answer to Echo), Draw, Discard, Fracture |
| Root | Summoning | Summon (tokens), Anchor, Heal, max Shard ramp, sturdy units |
| Neutral | Utility | Link spells, rare board-wide effects such as Tempest |

## Deck constraints that shape card design

A deck is 30 cards, from one faction plus neutral cards, with at most 2 copies of a card. Since decks cannot mix factions, a cross-faction combo can only happen through a neutral card. This is the main balance lever: for example, Ember's Sacrifice and Root's Anchor only meet if one side is neutral.

## Numbers

Player HP is 50 (rule 1.1). Follow the number scale in `content/cards/README.md` ("Balance guidelines"): roughly 2 points of value per Shard, so a unit dies to an attack of the same cost. It is a guideline, not a rule. The maintainer is happy to rebalance through patch notes when a card turns out wrong, since patches also feed the LLM patch experiment, so don't over-agonize over exact numbers.

## Balance watch-points

- Echo 300 + self-Sacrifice + Recall: a repeatable loop, limited only by Shards.
- Tokens: every token shields its player from attacks (7.3), so calibrate Summon carefully.
- Sacrifice on an anchored unit: the sacrifice is paid but the unit stays (11.3.3).
- Overcharge: it should sometimes be worth spamming. Check how stacked Shard locks play out.
- Fracture with self-drawbacks: it separates planning bots from greedy ones, which is valuable for the bot evaluation. Keep it rare.

Once the engine exists, validate new cards with `simulate_matches` (MCTS by default) rather than by intuition.

## Names and card text

- English names that evoke the faction (Ember: ash, cinder, pyre; Tide: current, brine, undertow; Root: bramble, sprout, thorn).
- Avoid names of real cards from existing TCGs, and keyword names that collide with known games: a base LLM would import its prior and skew the evaluation.
- Generate card rules text from the card's structured data rather than writing it freehand. The text indexed by RAG then always matches what the engine executes.
- Existing cards live in `content/cards/<faction>/`; check them for names already taken and for consistent power levels.

## Card JSON

The format is documented in `content/cards/README.md` and enforced by `content/cards/card.schema.json`. Read the README before writing a card file. The key points:

- One file per card at `content/cards/<faction>/<slug>.json`, with a stable `id` (`<faction>.<slug>`) that never changes, even on rename. Rulings, eval questions and decks reference cards by id.
- No rules text in the file. The text is generated from the data with `content/cards/text-templates.json`; only the optional `flavor` line is free text. If a card needs wording the templates can't produce, change the templates (or the rules), not the card.
- Echo lives on an attack (`attacks[].echo`), not in `keywords`. Fracture spells use `fracture` steps instead of `cost` + `effects`.
- Game versions are git tags (`patch-1.0`…), so never add version fields to cards.
- Some checks are beyond the schema (id matches path, Link only on neutral spells, Recall only on Ember, `summon.token` exists, `self` not on spells, Fracture 5 count). They are tested in `content/tests/test_card_rules.py`.
- After adding or editing cards, run the content tests from `content/`: `uv run pytest` (or `python -m pytest` with pytest and jsonschema installed). CI runs them too. If you add an effect, target, trigger or keyword to the schema, add its wording to `text-templates.json`, or `test_text_templates.py` fails.

When the schema and the rulebook disagree, the rulebook wins: raise it and fix the schema.
