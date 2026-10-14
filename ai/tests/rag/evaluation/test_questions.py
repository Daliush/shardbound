from dataset.paths import repo_root
from rag.evaluation.questions import read_questions

ROOT = repo_root()


def test_reads_every_question_with_its_citations_as_relevant_chunks():
    questions = {question.id: question for question in read_questions(ROOT, "v1", "dev")}

    assert len(questions) == 72
    assert sum(1 for question in questions.values() if question.answerable) == 61
    assert questions["card/neutral.binding-thread/faction"].relevant_ids == {"neutral.binding-thread"}
    assert questions["rule/2.2"].relevant_ids == {"2.2"}


def test_gives_an_unanswerable_question_no_relevant_chunk():
    unanswerable = [question for question in read_questions(ROOT, "v1", "dev") if not question.answerable]

    assert unanswerable and all(not question.relevant_ids for question in unanswerable)


def test_does_not_count_the_cards_a_scenario_only_needs_to_be_played():
    questions = {question.id: question for question in read_questions(ROOT, "v1", "dev")}

    filler = questions["death+trigger-order/ember.cinderling/tide.shoal-sprite/neutral.tempest/50-hp"]
    deck = questions["deck/ember-starter/card-count/+ember.kiln-wyrm"]
    assert "neutral.shardling" not in filler.relevant_ids
    assert deck.relevant_ids == {"2.1"}
