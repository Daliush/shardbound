"""The deck schema accepts every real deck and rejects malformed decks."""

import copy

import pytest
from jsonschema import Draft202012Validator

DECK = {
    "id": "test-deck",
    "name": "Test Deck",
    "faction": "ember",
    "cards": [{"card": "ember.test-card", "count": 2}],
}


def variant(change):
    deck = copy.deepcopy(DECK)
    change(deck)
    return deck


MUST_REJECT = {
    "missing cards": variant(lambda d: d.pop("cards")),
    "empty card list": variant(lambda d: d.update(cards=[])),
    "missing faction": variant(lambda d: d.pop("faction")),
    "neutral deck": variant(lambda d: d.update(faction="neutral")),
    "unknown faction": variant(lambda d: d.update(faction="void")),
    "bad id format": variant(lambda d: d.update(id="Test Deck")),
    "three copies": variant(lambda d: d["cards"][0].update(count=3)),
    "zero copies": variant(lambda d: d["cards"][0].update(count=0)),
    "malformed card id": variant(lambda d: d["cards"][0].update(card="Test Card")),
    "entry without count": variant(lambda d: d["cards"][0].pop("count")),
    "unknown field in an entry": variant(lambda d: d["cards"][0].update(foil=True)),
    "unknown field on the deck": variant(lambda d: d.update(format="standard")),
}


def test_deck_schema_is_a_valid_json_schema(deck_schema):
    Draft202012Validator.check_schema(deck_schema)


def test_base_deck_is_valid(deck_validator):
    # The broken decks below are variants of this one; if it were invalid, the rejections would prove nothing.
    assert deck_validator.is_valid(DECK)


def test_real_deck_matches_schema(deck_validator, deck):
    errors = [f"{'/'.join(map(str, e.path)) or '(root)'}: {e.message}" for e in deck_validator.iter_errors(deck)]
    assert not errors, errors


@pytest.mark.parametrize("name", MUST_REJECT)
def test_malformed_deck_is_rejected(deck_validator, name):
    assert not deck_validator.is_valid(MUST_REJECT[name])
