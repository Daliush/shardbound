"""Shared fixtures for the content tests: card files, the card schema and the text templates."""

import pytest
from jsonschema import Draft202012Validator

from cardlib import CARD_FILES, CARDS_DIR, load_json


def pytest_generate_tests(metafunc):
    # Any test that asks for `card_path` runs once per card file, named "faction/slug".
    if "card_path" in metafunc.fixturenames:
        metafunc.parametrize("card_path", CARD_FILES, ids=[f"{p.parent.name}/{p.stem}" for p in CARD_FILES])


@pytest.fixture(scope="session")
def schema():
    return load_json(CARDS_DIR / "card.schema.json")


@pytest.fixture(scope="session")
def validator(schema):
    return Draft202012Validator(schema)


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
