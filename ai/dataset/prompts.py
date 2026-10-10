"""Every prompt the dataset sends to the model: it rewords a question or checks a rewording, never sees an answer."""

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

ALL = (PARAPHRASE_SYSTEM, PARAPHRASE_USER, CHECK_PARAPHRASE_SYSTEM, CHECK_PARAPHRASE_USER)


def paraphrase(question: str) -> Prompt:
    return Prompt("paraphrase", PARAPHRASE_SYSTEM, PARAPHRASE_USER.format(question=question))


def check_paraphrase(original: str, rewrite: str) -> Prompt:
    return Prompt("check-paraphrase", CHECK_PARAPHRASE_SYSTEM,
                  CHECK_PARAPHRASE_USER.format(original=original, rewrite=rewrite))


def fingerprint() -> str:
    """A hash of every prompt, written into the manifest: a changed prompt makes another dataset."""
    return hashlib.sha256("\n␞\n".join(ALL).encode("utf-8")).hexdigest()
