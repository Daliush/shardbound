from arbiter.generation.answer import ANSWER_SCHEMA
from arbiter.generation.prompt import SYSTEM, ContextChunk, write_prompt

CONTEXT = [
    ContextChunk("7.3", "rule", "[7. Combat] **Attack target**: an enemy unit."),
    ContextChunk("ember.ash-warden", "card", "Ash Warden: Ember unit, costs 3 Shards, 6 defense."),
    ContextChunk("vocabulary.destroy", "term", "**Destroy**: board to graveyard."),
    ContextChunk("neutral.tempest", "card", "Tempest: Neutral spell, costs 7 Shards. Destroy all units."),
]


def test_puts_the_cards_first_then_the_rules_and_terms_each_in_the_order_given():
    prompt = write_prompt("What happens if I cast Tempest?", CONTEXT)

    assert prompt.user == (
        "<context>\n"
        '<card id="ember.ash-warden">Ash Warden: Ember unit, costs 3 Shards, 6 defense.</card>\n'
        '<card id="neutral.tempest">Tempest: Neutral spell, costs 7 Shards. Destroy all units.</card>\n'
        '<rule id="7.3">[7. Combat] **Attack target**: an enemy unit.</rule>\n'
        '<term id="vocabulary.destroy">**Destroy**: board to graveyard.</term>\n'
        "</context>\n"
        "\n"
        "<question>What happens if I cast Tempest?</question>")


def test_gives_every_question_the_same_instructions():
    assert write_prompt("What does Tempest do?", CONTEXT).system == SYSTEM
    assert write_prompt("Can I play two Brine Wards?", []).system == SYSTEM


def test_keeps_braces_in_a_question_as_they_are():
    assert "<question>What does {x} mean?</question>" in write_prompt("What does {x} mean?", []).user


def test_the_answer_schema_requires_its_three_fields_and_no_other():
    assert ANSWER_SCHEMA["required"] == ["answer", "citations", "answerable"]
    assert ANSWER_SCHEMA["additionalProperties"] is False
