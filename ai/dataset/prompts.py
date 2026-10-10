"""Every prompt the dataset sends to the model. The model writes or checks a question; it never sees an answer key."""

import hashlib

from dataset.llm import Prompt

_GAME = ("Shardbound is a card game for two players. Players ask a rules referee about cards, keywords and "
         "situations of their games.")

PARAPHRASE_SYSTEM = f"""{_GAME}
You rewrite template questions the way a player would type them to the referee: natural, sometimes casual, \
without changing what is asked."""

PARAPHRASE_USER = """Rewrite this question as a player would ask it.

Rules:
- Keep every card name and keyword exactly as written, with the same capital letters.
- Keep every number as digits.
- Keep who is speaking: "I", "my", "my opponent" stay the same people.
- Do not add, remove or change any card, unit, move, choice, number or fact.
- Do not name any card the question does not name.
- Do not answer the question.

<question>{question}</question>

Reply with the rewritten question only."""

CHECK_PARAPHRASE_SYSTEM = f"""{_GAME}
You check that a rewritten question still asks exactly what the original asks."""

CHECK_PARAPHRASE_USER = """Compare the rewrite with the original.

It passes only if all of these hold:
- it describes the same situation: the same cards, units, moves, choices, numbers and moment of the game;
- the same person is speaking, and "my" and "my opponent" point to the same players;
- it asks the same thing, and would have the same answer;
- it names no card or keyword the original does not name.

<original>{original}</original>
<rewrite>{rewrite}</rewrite>

Reply with JSON only: {{"verdict": "pass" or "fail", "reason": "one short sentence"}}"""

WRITE_RULE_QUESTION_SYSTEM = f"""{_GAME}
You write the questions players ask when they do not know a rule yet."""

WRITE_RULE_QUESTION_USER = """Here is one rule of the rulebook, from the section "{section}".

<rule>{rule}</rule>

Write one question a player could ask the referee whose complete answer is this rule.

Rules:
- Ask about what the rule settles, the way a player would: about their game, a card, or what a word means.
- The whole rule should be needed to answer it, and nothing else should be.
- Do not quote the rule, and do not mention rule numbers or the rulebook.
- Do not name any card.

Reply with the question only."""

CHECK_RULE_QUESTION_SYSTEM = f"""{_GAME}
You check that a player's question is answered by one rule of the rulebook."""

CHECK_RULE_QUESTION_USER = """<rule>{rule}</rule>
<question>{question}</question>

It passes only if all of these hold:
- this rule answers the question completely, without needing another rule;
- the question does not mention a rule number or the rulebook, and names no card;
- a player who does not know the rule could ask it.

Reply with JSON only: {{"verdict": "pass" or "fail", "reason": "one short sentence"}}"""

ALL = (PARAPHRASE_SYSTEM, PARAPHRASE_USER, CHECK_PARAPHRASE_SYSTEM, CHECK_PARAPHRASE_USER,
       WRITE_RULE_QUESTION_SYSTEM, WRITE_RULE_QUESTION_USER, CHECK_RULE_QUESTION_SYSTEM, CHECK_RULE_QUESTION_USER)


def paraphrase(question: str) -> Prompt:
    return Prompt("paraphrase", PARAPHRASE_SYSTEM, PARAPHRASE_USER.format(question=question))


def check_paraphrase(original: str, rewrite: str) -> Prompt:
    return Prompt("check-paraphrase", CHECK_PARAPHRASE_SYSTEM,
                  CHECK_PARAPHRASE_USER.format(original=original, rewrite=rewrite))


def write_rule_question(section: str, rule: str) -> Prompt:
    return Prompt("write-rule-question", WRITE_RULE_QUESTION_SYSTEM,
                  WRITE_RULE_QUESTION_USER.format(section=section, rule=rule))


def check_rule_question(rule: str, question: str) -> Prompt:
    return Prompt("check-rule-question", CHECK_RULE_QUESTION_SYSTEM,
                  CHECK_RULE_QUESTION_USER.format(rule=rule, question=question))


def fingerprint() -> str:
    """A hash of every prompt, written into the manifest: a changed prompt makes another dataset."""
    return hashlib.sha256("\n␞\n".join(ALL).encode("utf-8")).hexdigest()
