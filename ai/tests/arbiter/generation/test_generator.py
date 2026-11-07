from arbiter.generation.answer import Answer
from arbiter.generation.completion import Usage
from arbiter.generation.completion_cache import CompletionCache
from arbiter.generation.prompt import Prompt
from tests.arbiter.generation.scripted_generator import VALID_REPLY, ScriptedGenerator

PROMPT = Prompt("You are the rules referee.", "<question>What does Tempest do?</question>")


def test_returns_the_first_valid_answer_with_no_format_failure():
    generation = ScriptedGenerator([VALID_REPLY]).generate_answer(PROMPT)

    assert generation.answer == Answer(answer="Ash Warden is destroyed.", citations=["8.2"], answerable=True)
    assert generation.format_failures == []
    assert generation.usage == Usage(100, 20)
    assert generation.seconds == 1.5


def test_tries_once_more_when_the_reply_is_no_answer_and_counts_both_attempts_tokens_and_time():
    generator = ScriptedGenerator(['{"answer": "Ash Warden is destroyed."}', VALID_REPLY])

    generation = generator.generate_answer(PROMPT)

    assert generation.answer is not None
    assert generation.format_failures == ["stopped on end_turn: Field required (citations)"]
    assert generation.usage == Usage(200, 40)
    assert generation.seconds == 3.0
    assert generator.prompts == [PROMPT, PROMPT]


def test_gives_no_answer_after_two_replies_that_are_no_answer():
    generation = ScriptedGenerator(["", '{"answer": 3, "citations": [], "answerable": true}']).generate_answer(PROMPT)

    assert generation.answer is None
    assert generation.format_failures == ["stopped on end_turn: Invalid JSON: EOF while parsing a value at line 1 "
                                          "column 0",
                                          "stopped on end_turn: Input should be a valid string (answer)"]


def test_refuses_a_field_the_schema_does_not_have():
    reply = '{"answer": "Yes.", "citations": [], "answerable": true, "confidence": 0.9}'

    generation = ScriptedGenerator([reply, VALID_REPLY]).generate_answer(PROMPT)

    assert generation.format_failures == ["stopped on end_turn: Extra inputs are not permitted (confidence)"]


def test_reads_a_cached_completion_instead_of_calling_the_model_again_with_the_time_its_call_took(tmp_path):
    ScriptedGenerator([VALID_REPLY], CompletionCache(tmp_path)).generate_answer(PROMPT)
    generator = ScriptedGenerator([], CompletionCache(tmp_path))

    generation = generator.generate_answer(PROMPT)

    assert generation.answer is not None
    assert generation.seconds == 1.5
    assert generator.prompts == []


def test_caches_each_attempt_apart(tmp_path):
    ScriptedGenerator(["{}", VALID_REPLY], CompletionCache(tmp_path)).generate_answer(PROMPT)

    generation = ScriptedGenerator([], CompletionCache(tmp_path)).generate_answer(PROMPT)

    assert generation.answer is not None
    assert len(generation.format_failures) == 1
