from dataset.llm import FakeClient
from dataset.wording import code_check, parse_verdict, word

TEMPLATE = "My opponent played Root Sentinel on their last turn. If I cast Crystal Rupture on it, what happens to it?"


def test_keeps_a_rewrite_that_passes_both_checks(sources):
    client = FakeClient({"paraphrase": lambda prompt: "If I hit my opponent's Root Sentinel, played last turn, "
                                                      "with Crystal Rupture, what happens?"})

    wording = word(TEMPLATE, [], sources, client)

    assert wording.status == "paraphrase"
    assert wording.question.startswith("If I hit my opponent's Root Sentinel")
    assert [call.task for call in client.calls] == ["paraphrase", "check-paraphrase"]


def test_keeps_the_template_when_the_model_check_fails(sources):
    client = FakeClient({
        "paraphrase": lambda prompt: "If I cast Crystal Rupture on my own Root Sentinel, what happens?",
        "check-paraphrase": lambda prompt: '{"verdict": "fail", "reason": "the Sentinel changed sides"}',
    })

    wording = word(TEMPLATE, [], sources, client)

    assert (wording.question, wording.status) == (TEMPLATE, "template")
    assert wording.failure == "model check: the Sentinel changed sides"


def test_does_not_ask_the_model_when_code_already_rejects_the_rewrite(sources):
    client = FakeClient({"paraphrase": lambda prompt: "If I cast Crystal Rupture on their Sentinel, what happens?"})

    wording = word(TEMPLATE, [], sources, client)

    assert wording.status == "template"
    assert wording.failure == "drops Root Sentinel"
    assert [call.task for call in client.calls] == ["paraphrase"]


def test_code_rejects_a_dropped_or_added_card_a_changed_number_or_a_rule_number(sources):
    template = "It is turn 3, my turn. If I cast Rime Snare on Root Sentinel, what happens?"

    assert code_check(template, "Turn 3, my turn: Rime Snare on Root Sentinel, what happens?", [], sources) is None
    assert code_check(template, "Turn 3: Rime Snare on Sentinel?", [], sources) == "drops Root Sentinel"
    assert code_check(template, "Turn 3: Rime Snare on Root Sentinel and Tempest?", [], sources) == "adds Tempest"
    assert code_check(template, "Turn 4: Rime Snare on Root Sentinel?", [], sources) == "changes a number"
    assert code_check(template, "Turn 3: Rime Snare on Root Sentinel (8.10)?", [], sources) == "adds a rule number"
    assert code_check("What does the Siphon keyword do?", "What is Syphon?", ["Siphon"], sources) == "drops Siphon"


def test_reads_the_model_s_verdict_and_fails_one_it_cannot_read():
    assert parse_verdict('```json\n{"verdict": "pass", "reason": "same"}\n```').passed
    assert not parse_verdict('{"verdict": "fail", "reason": "other card"}').passed
    assert parse_verdict("Looks fine to me.").reason.startswith("unreadable verdict")
    assert not parse_verdict("Looks fine to me.").passed
