from rag.chunks import Chunk
from rag.context import ContextSelector


def card(chunk_id: str, name: str) -> Chunk:
    return Chunk(chunk_id, "card", name, f"{name}: a card.", (), "c", "h")


CHUNKS = [card("ember.ash-warden", "Ash Warden"), card("ember.roaring-pyre", "Roaring Pyre"),
          card("neutral.tempest", "Tempest")]
QUESTION = "I have Ash Warden on my board. What happens if I cast Roaring Pyre and then cast Tempest?"
RANKING = ["7.3", "ember.ash-warden", "7.2", "1.4", "11.3.4", "ember.roaring-pyre", "ember.pyre-offering", "7.7",
           "root.bramble-warden", "9.11", "neutral.tempest", "8.10"]


def test_keeps_the_first_ten_chunks_then_adds_the_named_cards_ranked_past_them():
    context = ContextSelector(CHUNKS).select_context(QUESTION, RANKING)

    assert context == [*RANKING[:10], "neutral.tempest"]


def test_adds_no_named_card_the_first_ten_already_hold():
    context = ContextSelector(CHUNKS).select_context("What does Ash Warden do?", RANKING)

    assert context == RANKING[:10]


def test_adds_a_card_named_twice_once():
    context = ContextSelector(CHUNKS).select_context("Tempest, then Tempest again?", ["7.3"])

    assert context == ["7.3", "neutral.tempest"]


def test_keeps_as_many_ranked_chunks_as_its_size():
    context = ContextSelector(CHUNKS, size=2).select_context("What does Tempest do?", RANKING)

    assert context == ["7.3", "ember.ash-warden", "neutral.tempest"]


def test_leaves_the_ranking_it_is_given_as_it_is():
    ranking = list(RANKING[:3])

    ContextSelector(CHUNKS).select_context(QUESTION, ranking)

    assert ranking == RANKING[:3]
