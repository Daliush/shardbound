import pytest

from arbiter.evaluation.answer_metrics import (ANSWER_METRICS, Answered, CitationPrecision, CitationRecall,
                                               CorrectRefusal, StepOrder, ValidAnswer, list_cited_steps)
from tests.arbiter.evaluation.answer_key_examples import ASH_WARDEN, BINDING_THREAD, CINDERLING, RAMPAGE, answer_citing


def test_valid_answer_is_whether_an_attempt_gave_an_answer():
    assert ValidAnswer().score(ASH_WARDEN, answer_citing("8.2")) == 1.0
    assert ValidAnswer().score(ASH_WARDEN, None) == 0.0


def test_answered_is_whether_the_arbiter_answers_a_question_that_has_an_answer():
    assert Answered().score(ASH_WARDEN, answer_citing("8.2")) == 1.0
    assert Answered().score(ASH_WARDEN, answer_citing(answerable=False)) == 0.0, "a refusal by mistake"
    assert Answered().score(ASH_WARDEN, None) == 0.0


def test_answered_leaves_out_a_question_that_has_no_answer():
    assert Answered().score(RAMPAGE, answer_citing(answerable=False)) is None


def test_correct_refusal_is_whether_the_arbiter_refuses_a_question_that_has_no_answer():
    assert CorrectRefusal().score(RAMPAGE, answer_citing(answerable=False)) == 1.0
    assert CorrectRefusal().score(RAMPAGE, answer_citing("11.1.1")) == 0.0, "an invented answer"
    assert CorrectRefusal().score(RAMPAGE, None) == 0.0


def test_correct_refusal_leaves_out_a_question_that_has_an_answer():
    assert CorrectRefusal().score(ASH_WARDEN, answer_citing(answerable=False)) is None


def test_citation_recall_is_the_share_of_the_key_s_citations_the_answer_cites():
    assert CitationRecall().score(ASH_WARDEN, answer_citing("8.5", "8.2", "7.2")) == 2 / 7
    assert CitationRecall().score(BINDING_THREAD, answer_citing("neutral.binding-thread")) == 1.0


def test_citation_recall_counts_a_citation_cited_twice_once():
    assert CitationRecall().score(ASH_WARDEN, answer_citing("8.2", "8.2")) == 1 / 7


def test_citation_recall_is_zero_without_an_answer_and_none_for_a_question_with_no_answer_to_give():
    assert CitationRecall().score(ASH_WARDEN, None) == 0.0
    assert CitationRecall().score(RAMPAGE, answer_citing(answerable=False)) is None


def test_citation_precision_is_the_share_of_right_ids_among_the_right_and_the_wrong_ones():
    assert CitationPrecision().score(ASH_WARDEN, answer_citing("8.5", "8.2", "7.2")) == 2 / 3
    assert CitationPrecision().score(BINDING_THREAD, answer_citing("neutral.binding-thread", "6.1")) == 1 / 2


def test_citation_precision_counts_neither_the_trace_s_other_rules_nor_the_cards_the_question_names():
    answer = answer_citing("8.5", "6.3", "ember.roaring-pyre", "7.2")

    assert CitationPrecision().score(ASH_WARDEN, answer) == 1 / 2, "8.5 right, 7.2 wrong, the others tolerated"


def test_citation_precision_counts_a_card_of_the_scenario_the_question_does_not_name_as_wrong():
    assert CitationPrecision().score(CINDERLING, answer_citing("8.2", "neutral.shardling")) == 1 / 2


def test_citation_precision_counts_an_id_cited_twice_once():
    assert CitationPrecision().score(ASH_WARDEN, answer_citing("8.2", "7.2", "8.2")) == 1 / 2


def test_citation_precision_leaves_out_an_answer_citing_only_tolerated_ids_or_nothing():
    assert CitationPrecision().score(ASH_WARDEN, answer_citing("ember.roaring-pyre", "neutral.tempest")) is None
    assert CitationPrecision().score(ASH_WARDEN, answer_citing()) is None


def test_citation_precision_leaves_out_no_answer_and_a_question_with_no_answer_to_give():
    assert CitationPrecision().score(ASH_WARDEN, None) is None
    assert CitationPrecision().score(RAMPAGE, answer_citing("11.1.1")) is None


def test_step_order_is_the_share_of_pairs_of_cited_rules_in_the_order_of_their_steps():
    assert StepOrder().score(ASH_WARDEN, answer_citing("8.5", "8.2", "11.1.1", "8.1")) == 1.0
    assert StepOrder().score(ASH_WARDEN, answer_citing("8.2", "8.5", "11.1.1", "8.1")) == 5 / 6, "one swap"
    assert StepOrder().score(ASH_WARDEN, answer_citing("8.1", "11.1.1", "8.2", "8.5")) == 0.0, "all reversed"


def test_step_order_leaves_the_order_within_one_step_free_and_reads_only_the_essential_rules():
    answer = answer_citing("ember.roaring-pyre", "8.5", "11.1.9", "8.2", "11.1.2", "11.1.1", "8.1")

    assert StepOrder().score(ASH_WARDEN, answer) == 1.0


def test_step_order_reads_the_steps_of_the_key_events():
    assert StepOrder().score(CINDERLING, answer_citing("8.2", "3.5", "6.3", "9.3", "8.1", "8.6")) == 1.0


def test_step_order_leaves_out_an_answer_citing_one_step_no_answer_and_a_question_without_a_scenario():
    assert StepOrder().score(ASH_WARDEN, answer_citing("11.1.1", "11.1.2")) is None
    assert StepOrder().score(ASH_WARDEN, None) is None
    assert StepOrder().score(BINDING_THREAD, answer_citing("neutral.binding-thread")) is None


def test_lists_the_step_of_each_essential_rule_cited_at_its_first_place():
    assert list_cited_steps([["8.5"], ["8.2"], ["8.1"]], ["8.2", "7.2", "8.5", "8.2", "8.1"]) == [1, 0, 2]


@pytest.mark.parametrize("metric", ANSWER_METRICS, ids=lambda metric: metric.key)
def test_every_metric_says_what_it_does_how_and_what_it_represents(metric):
    assert metric.description.path == f"mean_{metric.key}"
    assert metric.description.what.strip()
    assert metric.description.how.strip()
    assert metric.description.represents.strip()


def test_lists_every_metric_once_in_the_order_a_report_shows_them():
    keys = []
    for metric in ANSWER_METRICS:
        keys.append(metric.key)

    assert keys == ["valid_answer", "answered", "correct_refusal", "citation_recall", "citation_precision",
                    "step_order"]
