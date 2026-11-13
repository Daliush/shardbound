from arbiter.evaluation.evaluation_service import describe_run, evaluate_run
from rag.chunks import Chunk
from rag.contexts.named_cards.named_cards_context import NamedCardsContext
from rag.evaluation.runs import Layers
from rag.pipeline import Pipeline
from tests.arbiter.evaluation.answer_key_examples import ASH_WARDEN, RAMPAGE
from tests.arbiter.generation.scripted_generator import SCRIPTED_PRICE, VALID_REPLY, ScriptedGenerator
from tests.rag.retrievers.fixed_ranking import FixedRanking

CHUNKS = [Chunk("8.2", "rule", None, "[8. Effects] **Destroy**: board to graveyard.", (), "r", "h"),
          Chunk("ember.ash-warden", "card", "Ash Warden", "Ash Warden: Ember unit.", ("11.1.1",), "c", "h")]
PIPELINE_LAYERS = Layers(retriever="fixed", rerank="minilm-l6", context="named-cards")
REFUSAL = '{"answer": "The context does not say.", "citations": [], "answerable": false}'


def build_pipeline() -> Pipeline:
    return Pipeline([FixedRanking(["8.2"])], NamedCardsContext("named-cards", CHUNKS, follow_refs=False), CHUNKS)


def test_asks_the_arbiter_every_question_from_its_pipeline_s_context_and_scores_each_answer():
    generator = ScriptedGenerator([VALID_REPLY, REFUSAL], name="scripted")

    run = evaluate_run(PIPELINE_LAYERS, build_pipeline(), generator, [ASH_WARDEN, RAMPAGE])

    assert len(generator.prompts) == 2
    assert '<card id="ember.ash-warden">' in generator.prompts[0].user, "the card the question names joins the 10"
    questions = run.report.questions
    assert questions[0].context_ids == ["8.2", "ember.ash-warden"]
    assert questions[0].metric_values["citation_recall"] == 1 / 7
    assert questions[1].metric_values["correct_refusal"] == 1.0
    assert run.report.overall.question_count == 2


def test_names_the_run_by_the_pipeline_s_layers_and_the_generator_with_its_settings_and_price():
    run = evaluate_run(PIPELINE_LAYERS, build_pipeline(), ScriptedGenerator([VALID_REPLY]), [ASH_WARDEN])

    assert run.layers == Layers(retriever="fixed", rerank="minilm-l6", context="named-cards", answer="scripted")
    assert run.description == "The context of fixed + minilm-l6 + named-cards; then scripted replies"
    assert run.generator_settings == {"model": "scripted"}
    assert run.price == SCRIPTED_PRICE


def test_describes_the_pipeline_by_the_techniques_it_does_not_skip():
    layers = Layers(retriever="rrf-bm25-dense", rerank="minilm-l6", context="top10-plus-named-cards")

    assert describe_run(layers, ScriptedGenerator([])) == ("The context of rrf-bm25-dense + minilm-l6 + "
                                                          "top10-plus-named-cards; then scripted replies")
