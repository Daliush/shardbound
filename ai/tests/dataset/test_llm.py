import json

import httpx
import pytest

from dataset.llm import CachedClient, FakeClient, ModelApiError, OpenAiCompatibleClient, Prompt

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


def test_sends_a_reasoning_effort_only_when_one_is_set():
    sent = []

    def reply(request: httpx.Request) -> httpx.Response:
        sent.append(json.loads(request.content))
        return httpx.Response(200, json={"choices": [{"message": {"content": "ok"}}]})

    OpenAiCompatibleClient("https://models.test/v1", "luna-6", "secret", reasoning_effort="none",
                           transport=httpx.MockTransport(reply)).complete(PROMPT)

    assert sent[0]["reasoning_effort"] == "none"
    assert sent[0]["temperature"] == 0


def test_leaves_the_temperature_to_a_model_that_only_takes_its_own():
    sent = []

    def reply(request: httpx.Request) -> httpx.Response:
        sent.append(json.loads(request.content))
        return httpx.Response(200, json={"choices": [{"message": {"content": "ok"}}]})

    client = OpenAiCompatibleClient("https://models.test/v1", "luna-6", "secret", temperature=None,
                                    reasoning_effort="none", transport=httpx.MockTransport(reply))
    client.complete(PROMPT)

    assert "temperature" not in sent[0]
    assert client.options == {"reasoning_effort": "none"}


def test_shows_what_the_api_says_when_it_refuses_a_request():
    refusal = httpx.Response(400, json={"error": {"message": "Unsupported parameter: 'temperature'"}})
    client = OpenAiCompatibleClient("https://models.test/v1", "luna-6", "secret",
                                    transport=httpx.MockTransport(lambda request: refusal))

    with pytest.raises(ModelApiError, match="answered 400: .*Unsupported parameter: 'temperature'"):
        client.complete(PROMPT)


def test_retries_when_the_api_is_busy():
    replies = iter([httpx.Response(429), httpx.Response(503),
                    httpx.Response(200, json={"choices": [{"message": {"content": "ok"}}]})])
    client = OpenAiCompatibleClient("https://models.test/v1", "luna-6", "secret", backoff=0,
                                    transport=httpx.MockTransport(lambda request: next(replies)))

    assert client.complete(PROMPT) == "ok"


def test_needs_three_environment_variables_and_reads_the_optional_ones(monkeypatch):
    monkeypatch.setenv("TEST_BASE_URL", "https://models.test/v1")
    for name in ("TEST_MODEL", "TEST_KEY", "TEST_TEMPERATURE", "TEST_EFFORT"):
        monkeypatch.delenv(name, raising=False)
    variables = ("TEST_BASE_URL", "TEST_MODEL", "TEST_KEY", "TEST_TEMPERATURE", "TEST_EFFORT")

    with pytest.raises(SystemExit, match="Set TEST_MODEL, TEST_KEY"):
        OpenAiCompatibleClient.from_environment(*variables)

    monkeypatch.setenv("TEST_MODEL", "luna-6")
    monkeypatch.setenv("TEST_KEY", "secret")
    assert OpenAiCompatibleClient.from_environment(*variables).options == {"temperature": 0.0}
    monkeypatch.setenv("TEST_TEMPERATURE", "default")
    monkeypatch.setenv("TEST_EFFORT", "none")
    assert OpenAiCompatibleClient.from_environment(*variables).options == {"reasoning_effort": "none"}
    monkeypatch.setenv("TEST_TEMPERATURE", "0.2")
    assert OpenAiCompatibleClient.from_environment(*variables).options["temperature"] == 0.2
    monkeypatch.setenv("TEST_TEMPERATURE", "low")
    with pytest.raises(SystemExit, match='TEST_TEMPERATURE must be a number or "default"'):
        OpenAiCompatibleClient.from_environment(*variables)


def test_answers_a_prompt_seen_before_from_disk(tmp_path):
    inner = FakeClient()
    cached = CachedClient(inner, tmp_path)

    first = cached.complete(PROMPT)
    second = CachedClient(FakeClient({"paraphrase": lambda prompt: "changed"}), tmp_path).complete(PROMPT)

    assert first == second == "Quick rules question: How much does Shardling cost?"
    assert len(inner.calls) == 1
    assert len(list(tmp_path.rglob("*.json"))) == 1


def test_keeps_the_responses_of_two_models_or_two_settings_apart(tmp_path):
    class Other(FakeClient):
        model = "other"

    class Reasoning(FakeClient):
        options = {"reasoning_effort": "high"}

    for client in (FakeClient(), Other(), Reasoning()):
        CachedClient(client, tmp_path).complete(PROMPT)

    assert len(list(tmp_path.rglob("*.json"))) == 3
