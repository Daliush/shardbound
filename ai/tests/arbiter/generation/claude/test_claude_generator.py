from types import SimpleNamespace

import pytest

from arbiter.generation.answer import ANSWER_SCHEMA, ANSWER_SCHEMA_HASH
from arbiter.generation.claude.claude_generator import ClaudeGenerator
from arbiter.generation.completion import Price, Usage
from arbiter.generation.prompt import Prompt
from tests.arbiter.generation.scripted_generator import VALID_REPLY

PROMPT = Prompt("You are the rules referee.", "<question>What does Tempest do?</question>")
PRICE = Price(input_per_million=0.10, output_per_million=0.50)


def build_haiku(client: SimpleNamespace | None = None) -> ClaudeGenerator:
    return ClaudeGenerator("claude-haiku-5.5", "claude-haiku-5-5", "medium", PRICE, client=client)


class FakeMessages:
    """Answers every request with one response, and keeps the requests."""

    def __init__(self, response: SimpleNamespace) -> None:
        self.response = response
        self.requests: list[dict] = []

    def create(self, **request) -> SimpleNamespace:
        self.requests.append(request)
        return self.response


def fake_client(*content: SimpleNamespace, stop_reason: str = "end_turn") -> SimpleNamespace:
    usage = SimpleNamespace(input_tokens=1200, output_tokens=300)
    return SimpleNamespace(messages=FakeMessages(SimpleNamespace(content=list(content), stop_reason=stop_reason,
                                                                 usage=usage)))


def test_asks_its_model_for_a_reply_that_follows_the_answer_s_schema():
    client = fake_client(SimpleNamespace(type="text", text=VALID_REPLY))

    build_haiku(client).complete_prompt(PROMPT)

    assert client.messages.requests == [{
        "model": "claude-haiku-5-5",
        "max_tokens": 16000,
        "system": "You are the rules referee.",
        "messages": [{"role": "user", "content": "<question>What does Tempest do?</question>"}],
        "output_config": {"effort": "medium", "format": {"type": "json_schema", "schema": ANSWER_SCHEMA}},
    }]


def test_keeps_the_text_of_the_reply_and_leaves_its_thinking_out():
    client = fake_client(SimpleNamespace(type="thinking", thinking=""), SimpleNamespace(type="text", text=VALID_REPLY))

    completion = build_haiku(client).complete_prompt(PROMPT)

    assert completion.text == VALID_REPLY
    assert completion.usage == Usage(1200, 300)


def test_measures_how_long_the_call_took():
    completion = build_haiku(fake_client(SimpleNamespace(type="text", text=VALID_REPLY))).complete_prompt(PROMPT)

    assert 0 <= completion.seconds < 1


def test_keeps_why_the_model_stopped():
    client = fake_client(stop_reason="refusal")

    completion = build_haiku(client).complete_prompt(PROMPT)

    assert completion.text == ""
    assert completion.stop_reason == "refusal"


def test_names_its_model_effort_and_price():
    generator = build_haiku()

    assert generator.description == "Claude (claude-haiku-5-5, effort medium), with structured outputs"
    assert generator.settings == {"model": "claude-haiku-5-5", "effort": "medium", "max_tokens": 16000,
                                  "answer_schema": ANSWER_SCHEMA_HASH}
    assert generator.price == PRICE


def test_asks_for_the_key_when_none_is_set(monkeypatch):
    monkeypatch.delenv("CLAUDE_KEY", raising=False)
    generator = build_haiku()

    with pytest.raises(SystemExit, match="CLAUDE_KEY"):
        generator.complete_prompt(PROMPT)
