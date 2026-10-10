"""Words each template question the way a player would ask it, then checks the result before keeping it.

Code checks first what it can (card names, numbers, no rule number); then a second model call compares the rewrite
with the template. A rewrite that fails keeps its template, and the failure is recorded for the manifest.
"""

import json
import re
from dataclasses import dataclass

from dataset import prompts
from dataset.content import Content
from dataset.llm import LlmClient

_NUMBER = re.compile(r"\d+")
_RULE_ID = re.compile(r"\b\d+\.\d+(?:\.\d+)?\b")


@dataclass(frozen=True)
class Wording:
    question: str
    status: str  # "paraphrase" or "template"
    failure: str | None = None


def word(template: str, protected: list[str], content: Content, client: LlmClient) -> Wording:
    """`protected` are the names the rewrite must keep as they are, such as an invented card name."""
    rewrite = client.complete(prompts.paraphrase(template)).strip().strip('"').strip()
    failure = code_check(template, rewrite, protected, content)
    if failure is None:
        verdict = parse_verdict(client.complete(prompts.check_paraphrase(template, rewrite)))
        failure = None if verdict.passed else f"model check: {verdict.reason}"
    if failure is not None:
        return Wording(template, "template", failure)
    return Wording(rewrite, "paraphrase")


def code_check(template: str, rewrite: str, protected: list[str], content: Content) -> str | None:
    """The reason the rewrite fails what code can check, or None."""
    if not rewrite or "\n" in rewrite:
        return "not a one-line question"
    kept = {*content.names_in(template), *protected}
    missing = [name for name in kept if name not in rewrite]
    if missing:
        return f"drops {', '.join(sorted(missing))}"
    added = [name for name in content.names_in(rewrite) if name not in kept]
    if added:
        return f"adds {', '.join(sorted(added))}"
    if sorted(_NUMBER.findall(_RULE_ID.sub("", template))) != sorted(_NUMBER.findall(_RULE_ID.sub("", rewrite))):
        return "changes a number"
    if set(_RULE_ID.findall(rewrite)) - set(_RULE_ID.findall(template)):
        return "adds a rule number"
    return None


@dataclass(frozen=True)
class Verdict:
    passed: bool
    reason: str


def parse_verdict(reply: str) -> Verdict:
    """The model's JSON verdict; a reply that is not one fails, since nothing vouches for the question."""
    match = re.search(r"\{.*\}", reply, re.DOTALL)
    try:
        verdict = json.loads(match.group(0)) if match else {}
    except json.JSONDecodeError:
        verdict = {}
    if verdict.get("verdict") not in ("pass", "fail"):
        return Verdict(False, f"unreadable verdict: {reply[:80]!r}")
    return Verdict(verdict["verdict"] == "pass", str(verdict.get("reason", "")))
