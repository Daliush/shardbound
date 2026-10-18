from rag.chunks import Chunk
from rag.evaluation.evaluation_service import apply, continue_with, evaluate_combinations, score
from rag.evaluation.questions import Question
from rag.evaluation.runs import Layers
from rag.retrievers import Retriever
from rag.technique import Step
from tests.rag.evaluation.fake_techniques import CardAddingExpander, ReversingReranker

QUESTIONS = [
    Question("rule/2.2", "simple-rule", "Two factions in one deck?", True, frozenset({"2.2"})),
    Question("anchor", "interaction", "Tempest on my Root Sentinel?", True, frozenset({"11.3.2", "11.3.3"})),
    Question("rampage", "unanswerable", "What does Rampage do?", False, frozenset()),
]
ANSWERABLE = QUESTIONS[:2]
RANKINGS = {
    "Two factions in one deck?": ["2.1", "2.2"],
    "Tempest on my Root Sentinel?": ["11.3.2"] + [f"7.{n}" for n in range(1, 20)] + ["11.3.3"],
}


class FixedRetriever(Retriever):
    def __init__(self) -> None:
        super().__init__("fixed")
        self.asked: list[str] = []

    @property
    def description(self) -> str:
        return "fixed rankings"

    def rank_chunks(self, question: str) -> list[str]:
        self.asked.append(question)
        return RANKINGS[question]


def test_gives_a_retriever_nothing_and_the_next_step_the_retriever_s_first_twenty_chunks():
    reranker = ReversingReranker()

    apply(reranker, apply(FixedRetriever(), None, ANSWERABLE), ANSWERABLE)

    assert reranker.given == [["2.1", "2.2"], RANKINGS["Tempest on my Root Sentinel?"][:20]]


def test_builds_each_node_under_a_retriever_once_with_each_step_skipped_or_not():
    retriever = FixedRetriever()
    reranker = ReversingReranker()
    techniques = {Step.EXPAND: [CardAddingExpander()], Step.RERANK: [reranker]}

    runs = continue_with(apply(retriever, None, ANSWERABLE), [Step.EXPAND, Step.RERANK], techniques, ANSWERABLE)

    assert [run.layers for run in runs] == [Layers(retriever="fixed", rerank="reversing"),
                                            Layers(retriever="fixed", expand="card"),
                                            Layers(retriever="fixed", expand="card", rerank="reversing")]
    assert retriever.asked == ["Two factions in one deck?", "Tempest on my Root Sentinel?"], "each question once"
    assert reranker.given[2] == ["neutral.tempest", "2.1", "2.2"], "after the expander, what it handed on"


def test_scores_a_node_on_what_its_pipeline_handed_on():
    run = score(apply(ReversingReranker(), apply(FixedRetriever(), None, ANSWERABLE), ANSWERABLE))

    [deck_question, anchor_question] = run.report.questions
    assert run.layers == Layers(retriever="fixed", rerank="reversing")
    assert run.description == "fixed rankings; its top 20 reversed"
    assert deck_question.top_chunk_ids == ["2.2", "2.1"]
    assert deck_question.reciprocal_rank == 1.0
    assert anchor_question.relevant_ranks == {"11.3.2": 20, "11.3.3": None}, "11.3.3 was 21st"
    assert anchor_question.candidate_count == 20


def test_averages_each_metric_over_the_questions_then_by_category():
    report = score(apply(FixedRetriever(), None, ANSWERABLE)).report

    assert report.overall.question_count == 2
    assert report.overall.mean_recall_at == {10: (1.0 + 0.5) / 2, 20: (1.0 + 0.5) / 2}
    assert report.overall.mean_reciprocal_rank == (0.5 + 1.0) / 2
    assert report.overall.mean_candidate_count == (2 + 21) / 2
    assert list(report.by_category) == ["interaction", "simple-rule"]
    assert report.by_category["simple-rule"].mean_reciprocal_rank == 0.5
    assert report.by_category["interaction"].mean_recall_at[20] == 0.5


def test_keeps_each_question_first_twenty_chunks_and_finds_a_relevant_chunk_ranked_past_them():
    [deck_question, anchor_question] = score(apply(FixedRetriever(), None, ANSWERABLE)).report.questions

    assert deck_question.top_chunk_ids == ["2.1", "2.2"]
    assert len(anchor_question.top_chunk_ids) == 20
    assert anchor_question.relevant_ranks == {"11.3.2": 1, "11.3.3": 21}
    assert anchor_question.candidate_count == 21


def test_averages_a_recall_curve_that_agrees_with_recall_at_k():
    overall = score(apply(FixedRetriever(), None, ANSWERABLE)).report.overall

    assert len(overall.mean_recall_curve) == 21
    assert overall.mean_recall_curve[9] == overall.mean_recall_at[10]
    assert overall.mean_recall_curve[19] == overall.mean_recall_at[20]
    assert overall.mean_recall_curve[-1] == 1.0, "every relevant chunk is ranked"


def test_asks_only_the_answerable_questions_and_runs_each_retriever_then_every_pipeline_under_it():
    chunks = [Chunk("2.1", "rule", None, "[2. Deck building] A deck contains exactly 30 cards.", (), "r", "h1"),
              Chunk("2.2", "rule", None, "[2. Deck building] A deck holds cards of one faction.", (), "r", "h2")]

    runs = evaluate_combinations(["random", "bm25"], chunks, {Step.EXPAND: [], Step.RERANK: [ReversingReranker()]},
                                 QUESTIONS)

    assert [run.layers for run in runs] == [Layers(retriever="random"), Layers(retriever="random", rerank="reversing"),
                                            Layers(retriever="bm25"), Layers(retriever="bm25", rerank="reversing")]
    assert runs[0].report.overall.question_count == 2
