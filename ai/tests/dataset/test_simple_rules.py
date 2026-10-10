from dataset import simple_rules
from dataset.llm import FakeClient


def test_asks_about_every_rule_of_sections_1_to_11(rules):
    selected = simple_rules.selected(rules)

    assert {rule.section_number for rule in selected} == set(range(1, 12))
    assert len(selected) == len(rules)


def test_keeps_the_question_the_model_writes_when_its_check_passes(rules, sources):
    client = FakeClient({"write-rule-question": lambda prompt: '"Can a frozen unit still block an attack?"'})

    asked = simple_rules.ask(_rule(rules, "7.5"), sources, client)

    assert (asked.question, asked.failure) == ("Can a frozen unit still block an attack?", None)
    assert [call.task for call in client.calls] == ["write-rule-question", "check-rule-question"]
    assert "Intercept: when an attack targets one of their units" in client.calls[0].user
    assert "7.5" not in client.calls[0].user


def test_drops_a_question_that_cites_a_rule_or_names_a_card(rules, sources):
    citing = FakeClient({"write-rule-question": lambda prompt: "What does rule 7.5 say about intercepting?"})
    naming = FakeClient({"write-rule-question": lambda prompt: "Can my frozen Shardling intercept?"})

    assert simple_rules.ask(_rule(rules, "7.5"), sources, citing).failure == "cites the rulebook"
    assert simple_rules.ask(_rule(rules, "7.5"), sources, naming).failure == "names Shardling"
    assert simple_rules.ask(_rule(rules, "7.5"), sources, naming).question is None


def _rule(rules, rule_id):
    return next(rule for rule in rules if rule.id == rule_id)
