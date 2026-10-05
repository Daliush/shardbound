"""Shared fixtures for the content tests: card and deck files, their schemas and the text templates."""

import pytest
from jsonschema import Draft202012Validator

from contentlib import CARD_FILES, CARDS_DIR, DECK_FILES, DECKS_DIR, load_json


def pytest_generate_tests(metafunc):
    # Any test that asks for `card_path` runs once per card file ("faction/slug"),
    # and any test that asks for `deck_path` runs once per deck file.
    if "card_path" in metafunc.fixturenames:
        metafunc.parametrize("card_path", CARD_FILES, ids=[f"{p.parent.name}/{p.stem}" for p in CARD_FILES])
    if "deck_path" in metafunc.fixturenames:
        metafunc.parametrize("deck_path", DECK_FILES, ids=[p.stem for p in DECK_FILES])


@pytest.fixture(scope="session")
def schema():
    return load_json(CARDS_DIR / "card.schema.json")


@pytest.fixture(scope="session")
def validator(schema):
    return Draft202012Validator(schema)


@pytest.fixture(scope="session")
def deck_schema():
    return load_json(DECKS_DIR / "deck.schema.json")


@pytest.fixture(scope="session")
def deck_validator(deck_schema):
    return Draft202012Validator(deck_schema)


@pytest.fixture(scope="session")
def templates():
    return load_json(CARDS_DIR / "text-templates.json")


@pytest.fixture(scope="session")
def all_cards():
    """Every card, keyed by id."""
    return {card["id"]: card for card in map(load_json, CARD_FILES)}


@pytest.fixture
def card(card_path):
    return load_json(card_path)


@pytest.fixture
def deck(deck_path):
    return load_json(deck_path)
