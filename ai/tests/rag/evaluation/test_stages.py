from rag.evaluation.questions import Question
from rag.evaluation.runs import Layers
from rag.evaluation.stages import Stage, StepResult
from tests.rag.evaluation.fake_techniques import CardAddingExpander, ReversingReranker
from tests.rag.retrievers.fixed_ranking import FixedRanking

QUESTION = Question("anchor", "interaction", "Tempest on my Root Sentinel?", True, frozenset({"11.3.2"}))
RANKING = [f"7.{n}" for n in range(1, 26)]


def build_stage(technique, parent: Stage | None, chunk_ids: list[str], seconds: float) -> Stage:
    return Stage(technique, parent, {QUESTION.id: StepResult(QUESTION, chunk_ids, seconds)})


RETRIEVED = build_stage(FixedRanking(RANKING, "a fixed ranking"), None, RANKING, 0.5)
EXPANDED = build_stage(CardAddingExpander(), RETRIEVED, ["neutral.tempest", *RANKING[:20]], 0.25)
RERANKED = build_stage(ReversingReranker(), EXPANDED, ["7.20", "neutral.tempest"], 1.0)


def test_hands_on_a_retriever_s_first_twenty_chunks_and_the_whole_list_of_any_other_step():
    assert RETRIEVED.hand_on(QUESTION) == RANKING[:20]
    assert EXPANDED.hand_on(QUESTION) == ["neutral.tempest", *RANKING[:20]]


def test_names_the_technique_of_each_step_of_its_pipeline_and_none_for_a_skipped_step():
    assert RERANKED.name_layers() == Layers(retriever="fixed", expand="card", rerank="reversing")
    assert build_stage(ReversingReranker(), RETRIEVED, [], 0.0).name_layers() == Layers(retriever="fixed",
                                                                                      rerank="reversing")


def test_describes_each_step_of_its_pipeline_and_what_the_retriever_handed_on():
    assert RERANKED.describe() == "a fixed ranking; its top 20 with Tempest's card; reversed"


def test_adds_up_the_time_every_step_of_its_pipeline_took_for_a_question():
    assert RERANKED.sum_seconds(QUESTION) == 1.75
