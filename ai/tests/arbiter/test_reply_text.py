from arbiter.arbiter import Reply
from arbiter.generation import Generation
from arbiter.generation.answer import Answer
from arbiter.generation.completion import Usage
from arbiter.generation.prompt import ContextChunk
from arbiter.reply_text import format_reply

CONTEXT = [ContextChunk("8.2", "rule", "Destroy."), ContextChunk("neutral.tempest", "card", "Tempest.")]
ANSWER = Answer(answer="Every unit is destroyed.", citations=["neutral.tempest", "8.2"], answerable=True)


def test_prints_the_context_the_answer_its_citations_and_tokens():
    reply = Reply("What does Tempest do?", CONTEXT, Generation(ANSWER, [], Usage(1200, 300), 2.4))

    assert format_reply(reply) == (
        "Context (2 chunks): 8.2, neutral.tempest\n"
        "\n"
        "Every unit is destroyed.\n"
        "\n"
        "Citations: neutral.tempest, 8.2\n"
        "Answerable: yes\n"
        "Tokens: 1200 in, 300 out, in 2.4 s")


def test_prints_the_failure_before_a_second_attempt_s_answer():
    reply = Reply("?", CONTEXT, Generation(ANSWER, ["stopped on max_tokens: EOF"], Usage(2400, 600), 4.8))

    assert "Answerable: yes\nValid on attempt 2, after:\n  stopped on max_tokens: EOF\n" in format_reply(reply)


def test_prints_every_failure_when_no_attempt_gave_an_answer():
    reply = Reply("?", [], Generation(None, ["stopped on refusal: EOF", "stopped on refusal: EOF"], Usage(10, 0), 0.3))

    assert format_reply(reply) == (
        "Context (0 chunks): \n"
        "\n"
        "No valid answer after 2 attempts:\n"
        "  stopped on refusal: EOF\n"
        "  stopped on refusal: EOF\n"
        "Tokens: 10 in, 0 out, in 0.3 s")
