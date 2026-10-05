"""The card schema accepts every real card, rejects broken cards and accepts the legal edge cases.

The broken and edge-case cards are built from small synthetic cards defined here, so these tests
do not change when a real card is rebalanced.
"""

import copy

import pytest
from jsonschema import Draft202012Validator

UNIT = {
    "id": "ember.test-unit",
    "name": "Test Unit",
    "faction": "ember",
    "type": "unit",
    "cost": 3,
    "defense": 6,
    "attacks": [
        {
            "name": "Strike",
            "cost": 2,
            "effects": [{"effect": "damage", "amount": 4, "target": "attack_target"}],
            "echo": 50,
        },
        {
            "name": "Rally",
            "cost": 1,
            "effects": [
                {"effect": "modify", "attack_damage": 2, "defense": 0, "duration": "end_of_turn", "target": "all_ally_units"}
            ],
        },
    ],
}

SPELL = {
    "id": "ember.test-spell",
    "name": "Test Spell",
    "faction": "ember",
    "type": "spell",
    "cost": 1,
    "effects": [{"effect": "damage", "amount": 8, "target": "enemy_unit"}],
}

FRACTURE = {
    "id": "tide.test-fracture",
    "name": "Test Fracture",
    "faction": "tide",
    "type": "spell",
    "fracture": [
        {"cost": 1, "effects": [{"effect": "draw", "amount": 1, "target": "you"}]},
        {"cost": 2, "effects": [{"effect": "freeze", "target": "enemy_unit"}]},
        {"cost": 3, "effects": [{"effect": "freeze", "target": "all_enemy_units"}]},
    ],
}

RELIC = {
    "id": "tide.test-relic",
    "name": "Test Relic",
    "faction": "tide",
    "type": "relic",
    "cost": 3,
    "abilities": [
        {
            "trigger": "continuous",
            "effects": [{"effect": "aura", "kind": "stats", "target": "all_ally_units", "attack_damage": 1, "defense": 2}],
        }
    ],
}

TOKEN = {
    "id": "root.test-token",
    "name": "Test Token",
    "faction": "root",
    "type": "unit",
    "token": True,
    "defense": 2,
    "attacks": [{"cost": 1, "effects": [{"effect": "damage", "amount": 2, "target": "attack_target"}]}],
}

BASES = {"unit": UNIT, "spell": SPELL, "fracture": FRACTURE, "relic": RELIC, "token": TOKEN}


def variant(base, change):
    """A deep copy of `base` with `change` applied to it."""
    card = copy.deepcopy(base)
    change(card)
    return card


def add_attack(card):
    card["attacks"].append(copy.deepcopy(card["attacks"][0]))


MUST_REJECT = {
    # Structure of each card type
    "unit without attacks": variant(UNIT, lambda c: c.pop("attacks")),
    "unit with three attacks": variant(UNIT, add_attack),
    "unit without defense": variant(UNIT, lambda c: c.pop("defense")),
    "unit without cost (not a token)": variant(UNIT, lambda c: c.pop("cost")),
    "token with a cost": variant(TOKEN, lambda c: c.update(cost=1)),
    "token with a sacrifice cost": variant(TOKEN, lambda c: c.update(sacrifice_cost=1)),
    "relic without abilities": variant(RELIC, lambda c: c.pop("abilities")),
    "relic with defense": variant(RELIC, lambda c: c.update(defense=3)),
    "spell with both effects and fracture": variant(SPELL, lambda c: c.update(fracture=FRACTURE["fracture"])),
    "spell with neither effects nor fracture": variant(SPELL, lambda c: c.pop("effects")),
    "fracture spell with a top-level cost": variant(FRACTURE, lambda c: c.update(cost=2)),
    "fracture with 1 step": variant(FRACTURE, lambda c: c.update(fracture=c["fracture"][:1])),
    "fracture with 6 steps": variant(FRACTURE, lambda c: c.update(fracture=c["fracture"] * 2)),
    # Fields that must not exist
    "free-text rules field": variant(UNIT, lambda c: c.update(text="Deal 4 damage.")),
    "field from another card type": variant(UNIT, lambda c: c.update(fracture=[])),
    "unknown field inside an attack": variant(UNIT, lambda c: c["attacks"][0].update(range=2)),
    # Identity and numbers
    "bad id format": variant(UNIT, lambda c: c.update(id="Ember/Test Unit")),
    "unknown faction": variant(UNIT, lambda c: c.update(faction="void")),
    "unknown card type": variant(UNIT, lambda c: c.update(type="artifact")),
    "cost above 10": variant(UNIT, lambda c: c.update(cost=11)),
    "negative cost": variant(SPELL, lambda c: c.update(cost=-1)),
    "damage with amount 0": variant(SPELL, lambda c: c["effects"][0].update(amount=0)),
    "echo above 1000": variant(UNIT, lambda c: c["attacks"][0].update(echo=5000)),
    # Keywords in the wrong place
    "echo on a spell": variant(SPELL, lambda c: c.update(echo=50)),
    "echo as a card keyword": variant(UNIT, lambda c: c.update(keywords=["echo"])),
    "anchor on a spell": variant(SPELL, lambda c: c.update(keywords=["anchor"])),
    "anchor on a relic": variant(RELIC, lambda c: c.update(keywords=["anchor"])),
    # Targets
    "attack_target used in a spell": variant(SPELL, lambda c: c["effects"][0].update(target="attack_target")),
    "attack effect on an enemy unit other than the target": variant(
        UNIT, lambda c: c["attacks"][0]["effects"][0].update(target="enemy_unit")
    ),
    "attack effect on the opponent directly": variant(
        UNIT, lambda c: c["attacks"][0]["effects"][0].update(target="opponent")
    ),
    "damage targeting a relic": variant(SPELL, lambda c: c["effects"][0].update(target="enemy_relic")),
    # Effects in the wrong place, or malformed
    "link inside an attack": variant(UNIT, lambda c: c["attacks"][0]["effects"].append({"effect": "link"})),
    "aura in a spell": variant(
        SPELL,
        lambda c: c["effects"].append(
            {"effect": "aura", "kind": "stats", "target": "all_ally_units", "attack_damage": 1, "defense": 1}
        ),
    ),
    "non-aura effect in a continuous ability": variant(
        RELIC, lambda c: c["abilities"][0]["effects"].append({"effect": "draw", "amount": 1, "target": "you"})
    ),
    "attack trigger on a relic": variant(
        RELIC,
        lambda c: c["abilities"].append(
            {"trigger": "attack", "effects": [{"effect": "draw", "amount": 1, "target": "you"}]}
        ),
    ),
    "effect not in the closed list": variant(SPELL, lambda c: c.update(effects=[{"effect": "steal", "target": "enemy_unit"}])),
    "modify without duration": variant(UNIT, lambda c: c["attacks"][1]["effects"][0].pop("duration")),
    "discard without choice": variant(
        SPELL, lambda c: c.update(effects=[{"effect": "discard", "amount": 1, "target": "opponent"}])
    ),
    "gain_shards max with an amount": variant(
        SPELL, lambda c: c.update(effects=[{"effect": "gain_shards", "mode": "max", "amount": 2}])
    ),
    "gain_shards this_turn without amount": variant(
        SPELL, lambda c: c.update(effects=[{"effect": "gain_shards", "mode": "this_turn"}])
    ),
    "cost aura with change 0": variant(
        RELIC,
        lambda c: c["abilities"][0].update(
            effects=[{"effect": "aura", "kind": "cost", "player": "you", "card_type": "spell", "change": 0}]
        ),
    ),
    "summon with a malformed token id": variant(
        SPELL, lambda c: c.update(effects=[{"effect": "summon", "token": "Sprout", "count": 1}])
    ),
}

MUST_ACCEPT = {
    "attack with no target (self-buff only)": variant(UNIT, lambda c: c["attacks"].pop(0)),
    "attack with a side effect on its controller": variant(
        UNIT, lambda c: c["attacks"][0]["effects"].append({"effect": "draw", "amount": 1, "target": "you"})
    ),
    "attack that summons (no target)": variant(
        UNIT, lambda c: c["attacks"].__setitem__(1, {"cost": 2, "effects": [{"effect": "summon", "token": "root.sprout"}]})
    ),
    "cost aura on the opponent": variant(
        RELIC,
        lambda c: c["abilities"][0].update(
            effects=[{"effect": "aura", "kind": "cost", "player": "opponent", "card_type": "spell", "change": 1}]
        ),
    ),
    "destroy targeting a relic": variant(SPELL, lambda c: c.update(effects=[{"effect": "destroy", "target": "enemy_relic"}])),
    "death ability on a unit": variant(
        UNIT,
        lambda c: c.update(
            abilities=[{"trigger": "death", "effects": [{"effect": "damage", "amount": 2, "target": "opponent"}]}]
        ),
    ),
    "attack trigger on a unit": variant(
        UNIT,
        lambda c: c.update(abilities=[{"trigger": "attack", "effects": [{"effect": "freeze", "target": "enemy_unit"}]}]),
    ),
    "token with anchor": variant(TOKEN, lambda c: c.update(keywords=["anchor"])),
    "spell with a sacrifice cost and overcharge": variant(SPELL, lambda c: c.update(sacrifice_cost=1, keywords=["overcharge"])),
    "echo 300": variant(UNIT, lambda c: c["attacks"][0].update(echo=300)),
    "fracture with 5 steps": variant(FRACTURE, lambda c: c.update(fracture=c["fracture"] + c["fracture"][:2])),
}


def first_error(validator, card):
    error = next(iter(sorted(validator.iter_errors(card), key=lambda e: list(e.path))), None)
    if error is None:
        return None
    return f"at {'/'.join(map(str, error.path)) or '(root)'}: {error.message}"


def test_schema_is_a_valid_json_schema(schema):
    Draft202012Validator.check_schema(schema)


def test_real_card_matches_schema(validator, card):
    assert first_error(validator, card) is None, first_error(validator, card)


@pytest.mark.parametrize("name", BASES)
def test_synthetic_base_card_is_valid(validator, name):
    # The broken cards below are variants of these; if a base were invalid, the rejections would prove nothing.
    assert first_error(validator, BASES[name]) is None, first_error(validator, BASES[name])


@pytest.mark.parametrize("name", MUST_REJECT)
def test_broken_card_is_rejected(validator, name):
    assert not validator.is_valid(MUST_REJECT[name])


@pytest.mark.parametrize("name", MUST_ACCEPT)
def test_legal_edge_case_is_accepted(validator, name):
    assert first_error(validator, MUST_ACCEPT[name]) is None, first_error(validator, MUST_ACCEPT[name])
