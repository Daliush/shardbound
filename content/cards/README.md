# Card format (v0.1)

Every Shardbound card is a JSON file validated by [`card.schema.json`](card.schema.json). It is the single source of card data for the engine, the generated parts of the rulebook and the Python datasets. Rule IDs below refer to the [rulebook](../../docs/rules/README.md).

## Layout and identifiers

- One file per card: `content/cards/<faction>/<slug>.json`, for example `content/cards/ember/ash-warden.json`.
- Each card has a stable `id` of the form `<faction>.<slug>` (`ember.ash-warden`) that matches its path. **The id never changes**, even if the card is renamed: rulings, eval questions and decks point to cards by id.
- Tokens (`"token": true`) live in their faction's folder like any other unit.

## Rules text is generated, never written by hand

A card file holds data, not sentences. Its rules text is generated from that data with the wording in [`text-templates.json`](text-templates.json), so the text players and the Arbiter read always matches what the engine does, and the same effect is always worded the same way. The only free text is the optional `flavor` line, which has no game effect.

## Versions and patches

Game versions are git tags (`patch-1.0`, `patch-1.1`…), not fields in the card files. To see a card as it was in a given patch, check out the tag.

## Fields

| Field | Applies to | Meaning |
|---|---|---|
| `id`, `name`, `faction`, `type` | all | Required. `type` is `unit`, `spell` or `relic`. |
| `flavor` | all | Optional lore line. |
| `cost` | all except tokens and Fracture spells | Cost in Shards, 0 to 10. |
| `keywords` | units: `anchor`, `overcharge`; spells and relics: `overcharge` | Card-level keywords. Echo is not here: it belongs to an attack. |
| `sacrifice_cost` | all except tokens | Units of your own to sacrifice as an extra cost (8.3). |
| `defense` | units | Max defense. |
| `token` | units | `true` for a token: no cost, never in a deck. |
| `attacks` | units | One or two attack abilities: `name` (optional), `cost`, `effects`, `echo` (optional, Echo X). Effects on `attack_target` hit the attack's target; effects on `self`, `you` or `all_ally_units` affect your side. An attack with no `attack_target` effect has no target (7.8). |
| `abilities` | units, relics | Triggered or continuous abilities: `trigger` + `effects`. Continuous abilities only contain auras. |
| `effects` | spells | Effects resolved on cast. |
| `fracture` | spells | 2 to 5 steps, each with its own `cost` and `effects`. Replaces `cost` and `effects`. |

**Triggers**: `arrival`, `death`, `departure`, `turn_start`, `turn_end`, `continuous`, plus `attack` on units.

**Effects**: `damage`, `destroy`, `sacrifice`, `heal`, `modify`, `draw`, `discard`, `return_to_hand`, `summon`, `freeze`, `link`, `gain_shards`, `recall`, and `aura` inside continuous abilities. The schema lists the parameters and allowed targets of each one.

**Targets**: `ally_unit`, `enemy_unit`, `any_unit`, `random_ally_unit`, `random_enemy_unit`, `self`, `all_ally_units`, `all_enemy_units`, `all_units`, `you`, `opponent`, `any_player`, `ally_relic`, `enemy_relic`, `any_relic`, and `attack_target` inside attacks only.

## Examples

*Both examples are real cards from the first batch.*

**A unit** — `content/cards/ember/ash-warden.json`

```json
{
  "id": "ember.ash-warden",
  "name": "Ash Warden",
  "faction": "ember",
  "type": "unit",
  "cost": 3,
  "defense": 6,
  "attacks": [
    {
      "name": "Cinder Bite",
      "cost": 2,
      "effects": [{ "effect": "damage", "amount": 4, "target": "attack_target" }],
      "echo": 50
    },
    {
      "name": "Kindle",
      "cost": 1,
      "effects": [
        { "effect": "modify", "attack_damage": 2, "defense": 0, "duration": "end_of_turn", "target": "all_ally_units" }
      ]
    }
  ],
  "flavor": "The ashes remember."
}
```

Generated text:

> **Ash Warden** — Ember unit — 3 Shards — Defense 6
> Cinder Bite (2 Shards): Deal 4 damage to the target. Echo 50.
> Kindle (1 Shard): Give all your units +2/+0 until end of turn.
> *The ashes remember.*

Kindle has no target: it only buffs your side, so it cannot be intercepted (7.8).

**A Fracture spell** — `content/cards/tide/moonpull.json`

```json
{
  "id": "tide.moonpull",
  "name": "Moonpull",
  "faction": "tide",
  "type": "spell",
  "fracture": [
    {
      "cost": 1,
      "effects": [
        { "effect": "modify", "attack_damage": 2, "defense": 2, "duration": "permanent", "target": "ally_unit" }
      ]
    },
    {
      "cost": 2,
      "effects": [
        { "effect": "modify", "attack_damage": -2, "defense": -3, "duration": "permanent", "target": "enemy_unit" }
      ]
    },
    {
      "cost": 3,
      "effects": [{ "effect": "freeze", "target": "all_enemy_units" }]
    }
  ]
}
```

Generated text:

> **Moonpull** — Tide spell
> Fracture 3.
> Step 1 (1 Shard): Give an allied unit +2/+2.
> Step 2 (2 Shards): Give an enemy unit -2/-3.
> Step 3 (3 Shards): Freeze all enemy units.

## Balance guidelines (number scale)

These are rules of thumb for new cards, not game rules. If a card turns out too strong or too weak, it gets rebalanced in a patch: that is fine, and patches also feed the LLM patch experiment.

| What | Rule of thumb |
|---|---|
| Unit defense | 2 × cost + 1 (1 Shard → 3, 3 → 7, 5 → 11), a bit less if the unit has strong abilities |
| Attack damage | 2 × attack cost + 1 (1 Shard → 3, 2 → 5, 3 → 7), a bit less with extras such as Echo |
| Spell damage to a unit | About 2 per Shard |
| Damage to the player | About 1.5 per Shard, since it skips the units protecting the player |
| Heals and buffs | About 2 points per Shard |

The intent: a unit dies to an attack of the same cost, so trades are even, and once a board is cleared, 50 HP takes several turns of pressure to finish.

## Tests

Every card is checked automatically, locally and in CI ([`.github/workflows/content.yml`](../../.github/workflows/content.yml)), whenever `content/` changes. The tests live in [`content/tests/`](../tests/):

- `test_card_schema.py`: every card matches the schema; broken cards are rejected and legal edge cases accepted.
- `test_card_rules.py`: the rules the schema cannot express (below).
- `test_text_templates.py`: `text-templates.json` has wording for every effect, target, trigger and keyword the schema allows.

Run them from the `content/` folder:

```bash
uv run pytest
```

Without uv: `pip install pytest jsonschema`, then `python -m pytest`.

## Checks the schema cannot do

Enforced by `test_card_rules.py`:

- `id` matches the file path, and its faction prefix matches `faction`.
- Card names are unique.
- `link` only appears on neutral spells (8.11).
- `recall` only appears on Ember cards (8.13).
- `summon.token` points to an existing card with `"token": true`.
- `self` is only used by units and relics, not spells.
- At most two cards with Fracture 5 in the whole set.
- Deck rules (30 cards, one faction + neutrals, at most 2 copies, no tokens) are checked when a deck is built, not here.
