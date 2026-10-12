from dataset import jsonl
from dataset.paths import dataset_dir, repo_root
from rag.evaluation.questions import read_retrieval_questions

ROOT = repo_root()


def test_keeps_the_answerable_questions_with_their_citations_as_relevant_chunks():
    questions = read_retrieval_questions(ROOT, "v1", "dev")
    rows = jsonl.read(dataset_dir(ROOT, "v1") / "dataset-dev.jsonl")

    assert len(questions) == sum(1 for row in rows if row["answerable"]) == 61
    assert all(question.category != "unanswerable" and question.relevant_ids for question in questions)
    by_id = {question.id: question for question in questions}
    assert by_id["card/neutral.binding-thread/faction"].relevant_ids == {"neutral.binding-thread"}
    assert by_id["rule/2.2"].relevant_ids == {"2.2"}


def test_does_not_count_the_cards_a_scenario_only_needs_to_be_played():
    questions = {question.id: question for question in read_retrieval_questions(ROOT, "v1", "dev")}

    filler = questions["death+trigger-order/ember.cinderling/tide.shoal-sprite/neutral.tempest/50-hp"]
    deck = questions["deck/ember-starter/card-count/+ember.kiln-wyrm"]
    assert "neutral.shardling" not in filler.relevant_ids
    assert deck.relevant_ids == {"2.1"}
