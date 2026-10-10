import json

import httpx
import pytest

from dataset.llm import CachedClient, FakeClient, OpenAiCompatibleClient, Prompt

PROMPT = Prompt("paraphrase", "You rewrite questions.", "<question>How much does Shardling cost?</question>")


def test_calls_a_chat_completions_api_at_temperature_0():
    sent = []

    def reply(request: httpx.Request) -> httpx.Response:
        sent.append(request)
        return httpx.Response(200, json={"choices": [{"message": {"content": " What does Shardling cost? "}}]})

    client = OpenAiCompatibleClient("https://models.test/v1/", "luna-6", "secret",
                                    transport=httpx.MockTransport(reply))

    assert client.complete(PROMPT) == "What does Shardling cost?"
    body = json.loads(sent[0].content)
    assert str(sent[0].url) == "https://models.test/v1/chat/completions"
    assert sent[0].headers["Authorization"] == "Bearer secret"
    assert body == {"model": "luna-6", "temperature": 0, "messages": [
        {"role": "system", "content": PROMPT.system}, {"role": "user", "content": PROMPT.user}]}


def test_retries_when_the_api_is_busy():
    replies = iter([httpx.Response(429), httpx.Response(503),
                    httpx.Response(200, json={"choices": [{"message": {"content": "ok"}}]})])
    client = OpenAiCompatibleClient("https://models.test/v1", "luna-6", "secret", backoff=0,
                                    transport=httpx.MockTransport(lambda request: next(replies)))

    assert client.complete(PROMPT) == "ok"


def test_needs_its_three_environment_variables(monkeypatch):
    monkeypatch.setenv("TEST_BASE_URL", "https://models.test/v1")
    monkeypatch.delenv("TEST_MODEL", raising=False)
    monkeypatch.delenv("TEST_KEY", raising=False)

    with pytest.raises(SystemExit, match="Set TEST_MODEL, TEST_KEY"):
        OpenAiCompatibleClient.from_environment("TEST_BASE_URL", "TEST_MODEL", "TEST_KEY")


def test_answers_a_prompt_seen_before_from_disk(tmp_path):
    inner = FakeClient()
    cached = CachedClient(inner, tmp_path)

    first = cached.complete(PROMPT)
    second = CachedClient(FakeClient({"paraphrase": lambda prompt: "changed"}), tmp_path).complete(PROMPT)

    assert first == second == "Quick rules question: How much does Shardling cost?"
    assert len(inner.calls) == 1
    assert len(list(tmp_path.rglob("*.json"))) == 1


def test_keeps_the_responses_of_two_models_apart(tmp_path):
    class Other(FakeClient):
        model = "other"

    CachedClient(FakeClient(), tmp_path).complete(PROMPT)
    CachedClient(Other(), tmp_path).complete(PROMPT)

    assert len(list(tmp_path.rglob("*.json"))) == 2
