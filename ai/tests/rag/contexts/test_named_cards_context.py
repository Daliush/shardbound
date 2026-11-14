from rag.chunks import Chunk
from rag.contexts.named_cards.named_cards_context import NamedCardsContext
from rag.technique import Step


def card(chunk_id: str, name: str, refs: tuple[str, ...] = ()) -> Chunk:
    return Chunk(chunk_id, "card", name, f"{name}: a card.", refs, "c", "h")


CHUNKS = [card("ember.ash-warden", "Ash Warden", ("11.1.1", "11.1.2", "8.1", "8.5")),
          card("ember.roaring-pyre", "Roaring Pyre", ("8.5",)),
          card("neutral.tempest", "Tempest", ("8.2",))]
QUESTION = "I have Ash Warden on my board. What happens if I cast Roaring Pyre and then cast Tempest?"
RANKING = ["7.3", "ember.ash-warden", "7.2", "1.4", "11.3.4", "ember.roaring-pyre", "ember.pyre-offering", "7.7",
           "root.bramble-warden", "9.11", "neutral.tempest", "8.10"]


def build_context(follow_refs: bool = False, size: int = 10) -> NamedCardsContext:
    return NamedCardsContext("named-cards", CHUNKS, follow_refs, size)


def test_keeps_the_first_ten_chunks_then_adds_the_named_cards_ranked_past_them():
    context = build_context().build_context(QUESTION, RANKING)

    assert context == [*RANKING[:10], "neutral.tempest"]


def test_adds_no_named_card_the_first_ten_already_hold():
    context = build_context().build_context("What does Ash Warden do?", RANKING)

    assert context == RANKING[:10]


def test_adds_a_card_named_twice_once():
    context = build_context().build_context("Tempest, then Tempest again?", ["7.3"])

    assert context == ["7.3", "neutral.tempest"]


def test_keeps_as_many_ranked_chunks_as_its_size():
    context = build_context(size=2).build_context("What does Tempest do?", RANKING)

    assert context == ["7.3", "ember.ash-warden", "neutral.tempest"]


def test_leaves_the_ranking_it_is_given_as_it_is():
    ranking = list(RANKING[:3])

    build_context(follow_refs=True).build_context(QUESTION, ranking)

    assert ranking == RANKING[:3]


def test_adds_the_refs_of_the_named_cards_after_them_card_by_card_each_once():
    context = build_context(follow_refs=True).build_context(QUESTION, RANKING)

    assert context == [*RANKING[:10], "neutral.tempest", "11.1.1", "11.1.2", "8.1", "8.5", "8.2"]


def test_follows_the_refs_of_a_named_card_the_first_ten_already_hold():
    context = build_context(follow_refs=True).build_context("What does Ash Warden do?", RANKING)

    assert context == [*RANKING[:10], "11.1.1", "11.1.2", "8.1", "8.5"]


def test_adds_no_ref_the_first_ten_already_hold():
    context = build_context(follow_refs=True).build_context("What does Tempest do?", ["8.2", "7.3"])

    assert context == ["8.2", "7.3", "neutral.tempest"]


def test_follows_no_ref_of_a_card_the_question_does_not_name():
    context = build_context(follow_refs=True).build_context("What does Tempest do?", ["ember.ash-warden"])

    assert context == ["ember.ash-warden", "neutral.tempest", "8.2"]


def test_is_the_context_step_and_describes_what_it_adds():
    assert build_context().step is Step.CONTEXT
    assert build_context().description == "cut to the first 10 chunks, then the cards the question names"
    assert build_context(follow_refs=True).description == ("cut to the first 10 chunks, then the cards the question "
                                                           "names and the chunks these cards refer to, one level deep")


def test_builds_its_context_when_the_pipeline_processes_what_was_handed_on():
    assert build_context().process("What does Tempest do?", RANKING) == [*RANKING[:10], "neutral.tempest"]
