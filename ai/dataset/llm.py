"""The language model that words questions, behind one interface: the real API, a disk cache, and a fake.

The model only ever rewords or checks a question, never an answer (design doc §6.1).
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


class ModelApiError(RuntimeError):
    """The model's API refused a request: a wrong setting, a key or a quota, which a retry would not fix."""


class LlmClient(Protocol):
    model: str
    options: dict[str, str | float]

    def complete(self, prompt: Prompt) -> str: ...


class OpenAiCompatibleClient:
    """Any chat-completions API that follows OpenAI's format.

    `options` are the parameters sent besides the model and the messages: always the temperature, and
    `reasoning_effort` ("none", "low"…) when one is set, since rewording a question needs none.
    """

    def __init__(self, base_url: str, model: str, api_key: str, temperature: float,
                 reasoning_effort: str | None = None, timeout: float = 60.0, retries: int = 4,
                 transport: httpx.BaseTransport | None = None, backoff: float = 2.0):
        self.model = model
        self.options: dict[str, str | float] = {"temperature": temperature}
        if reasoning_effort:
            self.options["reasoning_effort"] = reasoning_effort
        self._retries = retries
        self._backoff = backoff
        self._http = httpx.Client(base_url=base_url.rstrip("/"), timeout=timeout, transport=transport,
                                  headers={"Authorization": f"Bearer {api_key}"})

    @classmethod
    def from_environment(cls, base_url_variable: str, model_variable: str, key_variable: str,
                         temperature_variable: str, reasoning_effort_variable: str,
                         default_temperature: float) -> "OpenAiCompatibleClient":
        """The first three variables are required; the temperature and the reasoning effort are optional."""
        missing = [name for name in (base_url_variable, model_variable, key_variable) if not os.environ.get(name)]
        if missing:
            raise SystemExit(f"Set {', '.join(missing)} to call the model, or use --fake for a dry run.")
        return cls(os.environ[base_url_variable], os.environ[model_variable], os.environ[key_variable],
                   temperature=_temperature(temperature_variable, default_temperature),
                   reasoning_effort=os.environ.get(reasoning_effort_variable) or None)

    def complete(self, prompt: Prompt) -> str:
        body = {
            "model": self.model,
            "messages": [{"role": "system", "content": prompt.system}, {"role": "user", "content": prompt.user}],
            **self.options,
        }
        for attempt in range(self._retries + 1):
            response = self._http.post("/chat/completions", json=body)
            if response.status_code == 429 or response.status_code >= 500:
                if attempt < self._retries:
                    time.sleep(self._backoff ** attempt if self._backoff else 0)
                    continue
            if response.is_error:
                raise ModelApiError(f"The model API answered {response.status_code}: {response.text[:500]}")
            return response.json()["choices"][0]["message"]["content"].strip()
        raise RuntimeError("unreachable")


class CachedClient:
    """Keeps every response on disk, by model, options and prompt: a rerun costs nothing and gives the same dataset."""

    def __init__(self, inner: LlmClient, cache_dir: Path):
        self.model = inner.model
        self.options = inner.options
        self._inner = inner
        self._dir = cache_dir

    def complete(self, prompt: Prompt) -> str:
        identity = [self.model, self.options, prompt.system, prompt.user]
        key = hashlib.sha256(json.dumps(identity, sort_keys=True).encode("utf-8")).hexdigest()
        file = self._dir / key[:2] / f"{key}.json"
        if file.exists():
            return json.loads(file.read_text(encoding="utf-8"))["response"]
        response = self._inner.complete(prompt)
        file.parent.mkdir(parents=True, exist_ok=True)
        record = {"model": self.model, "options": self.options, "task": prompt.task, "system": prompt.system,
                  "user": prompt.user, "response": response}
        file.write_text(json.dumps(record, ensure_ascii=False, indent=1), encoding="utf-8")
        return response


class FakeClient:
    """Answers without a model, for the tests and a dry run: a fixed reply per task, which a test can replace."""

    model = "fake"
    options: dict[str, str | float] = {}

    def __init__(self, replies: dict[str, Callable[[Prompt], str]] | None = None):
        self._replies = {
            "paraphrase": lambda prompt: "Quick rules question: " + _between(prompt.user, "<question>", "</question>"),
            "check-paraphrase": lambda prompt: '{"verdict": "pass", "reason": "fake"}',
            **(replies or {}),
        }
        self.calls: list[Prompt] = []

    def complete(self, prompt: Prompt) -> str:
        self.calls.append(prompt)
        return self._replies[prompt.task](prompt)


def _temperature(variable: str, default: float) -> float:
    value = os.environ.get(variable, "").strip()
    try:
        return float(value) if value else default
    except ValueError:
        raise SystemExit(f"{variable} must be a number, not {value!r}.") from None


def _between(text: str, start: str, end: str) -> str:
    return text.split(start, 1)[1].split(end, 1)[0].strip()
