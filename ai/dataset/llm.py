"""The language model that words questions, behind one interface: the real API, a disk cache, and a fake.

The model only ever writes or checks a question, never an answer (design doc §6.1).
"""

import hashlib
import json
import os
import time
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

import httpx


@dataclass(frozen=True)
class Prompt:
    """`task` names the job ("paraphrase", "check-paraphrase"…) for the fake client and the logs; it is not sent."""

    task: str
    system: str
    user: str


class LlmClient(Protocol):
    model: str

    def complete(self, prompt: Prompt) -> str: ...


class OpenAiCompatibleClient:
    """Any chat-completions API that follows OpenAI's format, at temperature 0."""

    def __init__(self, base_url: str, model: str, api_key: str, timeout: float = 60.0, retries: int = 4,
                 transport: httpx.BaseTransport | None = None, backoff: float = 2.0):
        self.model = model
        self._retries = retries
        self._backoff = backoff
        self._http = httpx.Client(base_url=base_url.rstrip("/"), timeout=timeout, transport=transport,
                                  headers={"Authorization": f"Bearer {api_key}"})

    @classmethod
    def from_environment(cls, base_url_variable: str, model_variable: str, key_variable: str) -> "OpenAiCompatibleClient":
        missing = [name for name in (base_url_variable, model_variable, key_variable) if not os.environ.get(name)]
        if missing:
            raise SystemExit(f"Set {', '.join(missing)} to call the model, or use --fake for a dry run.")
        return cls(os.environ[base_url_variable], os.environ[model_variable], os.environ[key_variable])

    def complete(self, prompt: Prompt) -> str:
        body = {
            "model": self.model,
            "temperature": 0,
            "messages": [{"role": "system", "content": prompt.system}, {"role": "user", "content": prompt.user}],
        }
        for attempt in range(self._retries + 1):
            response = self._http.post("/chat/completions", json=body)
            if response.status_code == 429 or response.status_code >= 500:
                if attempt < self._retries:
                    time.sleep(self._backoff ** attempt if self._backoff else 0)
                    continue
            response.raise_for_status()
            return response.json()["choices"][0]["message"]["content"].strip()
        raise RuntimeError("unreachable")


class CachedClient:
    """Keeps every response on disk, by model and prompt: a rerun costs nothing and gives the same dataset."""

    def __init__(self, inner: LlmClient, cache_dir: Path):
        self.model = inner.model
        self._inner = inner
        self._dir = cache_dir

    def complete(self, prompt: Prompt) -> str:
        key = hashlib.sha256(json.dumps([self.model, prompt.system, prompt.user]).encode("utf-8")).hexdigest()
        file = self._dir / key[:2] / f"{key}.json"
        if file.exists():
            return json.loads(file.read_text(encoding="utf-8"))["response"]
        response = self._inner.complete(prompt)
        file.parent.mkdir(parents=True, exist_ok=True)
        record = {"model": self.model, "task": prompt.task, "system": prompt.system, "user": prompt.user,
                  "response": response}
        file.write_text(json.dumps(record, ensure_ascii=False, indent=1), encoding="utf-8")
        return response


class FakeClient:
    """Answers without a model, for the tests and a dry run: a fixed reply per task, which a test can replace."""

    model = "fake"

    def __init__(self, replies: dict[str, Callable[[Prompt], str]] | None = None):
        self._replies = {
            "paraphrase": lambda prompt: "Quick rules question: " + _between(prompt.user, "<question>", "</question>"),
            "check-paraphrase": lambda prompt: '{"verdict": "pass", "reason": "fake"}',
            "write-rule-question": lambda prompt: "How does this part of the rules work: "
                                                  + _between(prompt.user, "<rule>", "</rule>")[:60].rstrip() + "?",
            "check-rule-question": lambda prompt: '{"verdict": "pass", "reason": "fake"}',
            **(replies or {}),
        }
        self.calls: list[Prompt] = []

    def complete(self, prompt: Prompt) -> str:
        self.calls.append(prompt)
        return self._replies[prompt.task](prompt)


def _between(text: str, start: str, end: str) -> str:
    return text.split(start, 1)[1].split(end, 1)[0].strip()
