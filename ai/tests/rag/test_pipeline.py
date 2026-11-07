from rag.chunks import Chunk
from rag.context import ContextSelector
from rag.pipeline import Pipeline
from tests.rag.evaluation.fake_techniques import CardAddingExpander, ReversingReranker
from tests.rag.retrievers.fixed_ranking import FixedRanking

RANKING = [f"7.{n}" for n in range(1, 26)]
CHUNKS = [Chunk("neutral.tempest", "card", "Tempest", "Tempest: Destroy all units.", ("8.2",), "c", "h")]
for rule_id in RANKING:
    CHUNKS.append(Chunk(rule_id, "rule", None, f"Rule {rule_id}.", (), "r", "h"))


def list_ids(chunks: list[Chunk]) -> list[str]:
    ids = []
    for chunk in chunks:
        ids.append(chunk.id)
    return ids


def test_hands_each_step_what_the_step_before_hands_on():
    reranker = ReversingReranker()
    pipeline = Pipeline([FixedRanking(RANKING), CardAddingExpander(), reranker], ContextSelector(CHUNKS), CHUNKS)

    pipeline.retrieve_context("What does Tempest do?")

    assert reranker.given == [["neutral.tempest", *RANKING[:20]]]


def test_returns_the_context_of_the_last_step_s_ranking_as_chunks():
    pipeline = Pipeline([FixedRanking(RANKING), ReversingReranker()], ContextSelector(CHUNKS), CHUNKS)

    context = pipeline.retrieve_context("What does Tempest do?")

    assert list_ids(context) == [*reversed(RANKING[:20])][:10] + ["neutral.tempest"]
    assert context[-1].text == "Tempest: Destroy all units."


def test_cuts_a_retriever_alone_at_the_context_s_size():
    pipeline = Pipeline([FixedRanking(RANKING)], ContextSelector(CHUNKS), CHUNKS)

    assert list_ids(pipeline.retrieve_context("Which rules apply?")) == RANKING[:10]
