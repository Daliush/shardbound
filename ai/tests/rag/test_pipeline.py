import pytest

from rag.chunks import Chunk
from rag.contexts.named_cards.named_cards_context import NamedCardsContext
from rag.evaluation.runs import Layers
from rag.pipeline import Pipeline, build_pipeline
from tests.rag.evaluation.fake_techniques import CardAddingExpander, ReversingReranker
from tests.rag.retrievers.fixed_ranking import FixedRanking

RANKING = [f"7.{n}" for n in range(1, 26)]
CHUNKS = [Chunk("neutral.tempest", "card", "Tempest", "Tempest: Destroy all units.", ("8.2",), "c", "h")]
for rule_id in RANKING:
    CHUNKS.append(Chunk(rule_id, "rule", None, f"Rule {rule_id}.", (), "r", "h"))
CONTEXT = NamedCardsContext("named-cards", CHUNKS, follow_refs=False)


def list_ids(chunks: list[Chunk]) -> list[str]:
    ids = []
    for chunk in chunks:
        ids.append(chunk.id)
    return ids


def test_hands_each_step_what_the_step_before_hands_on():
    reranker = ReversingReranker()
    pipeline = Pipeline([FixedRanking(RANKING), CardAddingExpander(), reranker], CONTEXT, CHUNKS)

    pipeline.retrieve_context("What does Tempest do?")

    assert reranker.given == [["neutral.tempest", *RANKING[:20]]]


def test_returns_the_context_of_the_last_step_s_ranking_as_chunks():
    pipeline = Pipeline([FixedRanking(RANKING), ReversingReranker()], CONTEXT, CHUNKS)

    context = pipeline.retrieve_context("What does Tempest do?")

    assert list_ids(context) == [*reversed(RANKING[:20])][:10] + ["neutral.tempest"]
    assert context[-1].text == "Tempest: Destroy all units."


def test_cuts_a_retriever_alone_at_the_context_s_size():
    pipeline = Pipeline([FixedRanking(RANKING)], CONTEXT, CHUNKS)

    assert list_ids(pipeline.retrieve_context("Which rules apply?")) == RANKING[:10]


def test_refuses_to_build_a_pipeline_whose_layers_name_no_context():
    with pytest.raises(ValueError, match="context"):
        build_pipeline(Layers(retriever="bm25"), CHUNKS)
