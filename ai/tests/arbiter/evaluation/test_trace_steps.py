import pytest

from arbiter.evaluation.trace_steps import find_steps, list_trace_rules, read_event_rules
from tests.arbiter.evaluation.answer_key_examples import ASH_WARDEN, CINDERLING


def test_reads_the_rules_each_event_carries():
    assert read_event_rules(["CardPlayed[6.3]", "EchoTriggered[11.1.1, 11.1.2]", "TurnEnded[]"]) == [
        {"6.3"}, {"11.1.1", "11.1.2"}, set()]


def test_refuses_what_is_no_event_of_a_trace():
    with pytest.raises(ValueError, match="UnitDestroyed 8.2"):
        read_event_rules(["UnitDestroyed 8.2"])


def test_lists_every_rule_of_the_trace_key_events_or_not():
    assert list_trace_rules(ASH_WARDEN.trace) == {"6.3", "8.5", "3.5", "8.2", "11.1.1", "11.1.2", "8.1"}


def test_groups_the_rules_one_event_carries_into_one_step():
    assert find_steps(ASH_WARDEN.rules, ASH_WARDEN.trace) == [["8.5"], ["8.2"], ["11.1.1", "11.1.2"], ["8.1"]]


def test_follows_the_order_of_the_key_events_not_the_first_event_that_carries_a_rule():
    steps = find_steps(CINDERLING.rules, CINDERLING.trace)

    assert steps == [["8.2"], ["6.3", "3.5"], ["9.3"], ["8.1"], ["8.6"]], "CardPlayed[6.3] is no key event"
