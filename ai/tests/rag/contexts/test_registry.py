from rag.baseline import BASELINE
from rag.chunks import Chunk
from rag.contexts.named_cards.named_cards_context import NamedCardsContext
from rag.contexts.registry import build_context, list_context_names

CHUNKS = [Chunk("neutral.tempest", "card", "Tempest", "Tempest: a card.", ("8.2",), "c", "h")]


def test_names_every_context_the_baseline_s_among_them():
    assert list_context_names() == ["top10-plus-named-cards", "top10-plus-named-cards-refs"]
    assert BASELINE.context in list_context_names()


def test_builds_a_context_by_its_name_and_gives_it_that_name():
    context = build_context("top10-plus-named-cards", CHUNKS)

    assert isinstance(context, NamedCardsContext)
    assert context.name == "top10-plus-named-cards"


def test_follows_the_named_cards_refs_only_in_the_context_named_for_them():
    question = "What does Tempest do?"

    assert build_context("top10-plus-named-cards", CHUNKS).build_context(question, []) == ["neutral.tempest"]
    assert build_context("top10-plus-named-cards-refs", CHUNKS).build_context(question, []) == ["neutral.tempest",
                                                                                                "8.2"]
