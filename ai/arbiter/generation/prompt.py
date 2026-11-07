"""
The prompt, the same for every configuration (design doc §5.2). It stays generic: a hint aimed at one interaction
("watch rule 11.3.6") would tune the Arbiter to that interaction's test questions.
"""

from dataclasses import dataclass

SYSTEM = """You are the rules referee of Shardbound, a card game for two players. Players ask you about cards, \
keywords and situations of their games.

Answer only from the context you are given: rules, vocabulary terms and cards, each in a tag that carries its id. \
Do not use what you know of other card games.
- Say what happens, in the order it happens, with every number.
- Cite the ids of the rules and cards your answer relies on, in the order they apply.
- When the context does not hold the answer, say so, set answerable to false and cite nothing."""

USER = """<context>
{context}
</context>

<question>{question}</question>"""


@dataclass(frozen=True)
class ContextChunk:
    """A chunk of the context as the prompt shows it. Generation reads no RAG type, so that a configuration without
    RAG answers too."""

    id: str
    kind: str
    """"rule", "term" or "card": the tag the prompt puts it in."""
    text: str


@dataclass(frozen=True)
class Prompt:
    system: str
    user: str


def write_prompt(question: str, context: list[ContextChunk]) -> Prompt:
    """The instructions, then the context's cards, then its rules and terms, each in the order the context gives them,
    then the question."""
    cards = []
    others = []
    for chunk in context:
        if chunk.kind == "card":
            cards.append(chunk)
        else:
            others.append(chunk)
    tags = []
    for chunk in cards + others:
        tags.append(f'<{chunk.kind} id="{chunk.id}">{chunk.text}</{chunk.kind}>')
    return Prompt(SYSTEM, USER.format(context="\n".join(tags), question=question))
