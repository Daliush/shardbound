"""One question per rule of sections 1 to 11, written by the model; the rule itself is the answer key.

The model writes the question only. The key facts are the rule's own sentences and the essential rule its id.
A question that fails its check has no template to fall back on, so it is dropped and recorded.
"""

import re
from dataclasses import dataclass

from dataset import prompts
from dataset.content import Content
from dataset.llm import LlmClient
from dataset.rulebook import Rule
from dataset.wording import parse_verdict

SECTIONS = range(1, 12)
_RULE_ID = re.compile(r"\b\d+\.\d+(?:\.\d+)?\b")


@dataclass(frozen=True)
class RuleQuestion:
    rule: Rule
    question: str | None
    failure: str | None = None


def ask(rule: Rule, content: Content, client: LlmClient) -> RuleQuestion:
    question = client.complete(prompts.write_rule_question(f"{rule.section} {rule.title}", rule.text)).strip()
    question = question.strip('"').strip()
    failure = _code_check(question, content)
    if failure is None:
        verdict = parse_verdict(client.complete(prompts.check_rule_question(rule.text, question)))
        failure = None if verdict.passed else f"model check: {verdict.reason}"
    return RuleQuestion(rule, None if failure else question, failure)


def selected(rules: list[Rule]) -> list[Rule]:
    return [rule for rule in rules if rule.section_number in SECTIONS]


def _code_check(question: str, content: Content) -> str | None:
    if not question or "\n" in question:
        return "not a one-line question"
    if _RULE_ID.search(question) or "rulebook" in question.lower():
        return "cites the rulebook"
    cards = [name for name in content.names_in(question) if name in content.card_names]
    if cards:
        return f"names {', '.join(cards)}"
    return None
