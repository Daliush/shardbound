"""Questions the Arbiter must decline: invented keywords, cards and mechanics, and the rulebook's open points.

No model is involved. The answer key is an abstention: `answerable` is false and there is no key fact.
"""

import re
from dataclasses import dataclass

from dataset.content import Content

INVENTED_KEYWORDS = ("Siphon", "Bulwark", "Quickcast", "Ricochet", "Vanguard", "Resonance",
                     "Shroud", "Rampage", "Lifebind", "Stormcall", "Mirage", "Kindred")

INVENTED_CARDS = ("Gloomfang Stalker", "Ironbark Colossus", "Tidecaller Siren", "Emberwing Phoenix",
                  "Shardglass Golem", "Mistveil Oracle", "Cinderhorn Ram", "Coralspine Leviathan",
                  "Thornveil Druid", "Voidshard Totem", "Ashen Effigy", "Brineglass Mirror")

INVENTED_MECHANICS = ("a Shard Surge", "the Reserve zone", "a Rally token", "the Overdrive phase",
                      "a Bounty counter", "the Ascension track", "a Fusion of two units",
                      "the Banish pile", "a Ward shield", "a Tribute draw")

_KEYWORD_QUESTIONS = (
    "What does the {name} keyword do?",
    "My unit has {name}. Does that change how it attacks?",
    "If a unit with {name} is hit by Tempest, what happens to it?",
)
_CARD_QUESTIONS = (
    "How much does {name} cost?",
    "What does {name} do?",
    "Can I put 2 copies of {name} in my Ember deck?",
)
_MECHANIC_QUESTIONS = (
    "How does {name} work?",
    "When during my turn can I use {name}?",
)

# The open points of docs/rules/12-open-points.md a player could ask about: the title the file gives each, the words
# a rewrite must keep, and the questions.
OPEN_POINTS = {
    "after-attack": ('"After attack" trigger', ("After attack",), (
        'My unit has an "After attack: sacrifice this unit" ability. Does it die before or after its attack hits?',
        'When does an "After attack" ability resolve?',
        'Does an "After attack" ability still resolve if the target left the board during the attack?',
    )),
    "unit-size": ("Unit size", (), (
        "Does a unit that takes 2 places on the board count as 2 sacrifices?",
        "I have 5 units on the board. Can I play a unit of size 2?",
        "How many places on the board does a size 2 unit take?",
    )),
}


@dataclass(frozen=True)
class Question:
    id: str
    group: str
    question: str
    source: str
    protected: tuple[str, ...]


def questions() -> list[Question]:
    found: list[Question] = []
    for kind, names, templates in (("keyword", INVENTED_KEYWORDS, _KEYWORD_QUESTIONS),
                                   ("card", INVENTED_CARDS, _CARD_QUESTIONS),
                                   ("mechanic", INVENTED_MECHANICS, _MECHANIC_QUESTIONS)):
        for name in names:
            for index, template in enumerate(templates, start=1):
                text = template.format(name=name)
                found.append(Question(f"unanswerable/{kind}/{_slug(name)}/{index}", f"invented/{_slug(name)}",
                                      text[0].upper() + text[1:], "invented", (_bare(name),)))
    for point, (_, protected, texts) in OPEN_POINTS.items():
        for index, text in enumerate(texts, start=1):
            found.append(Question(f"unanswerable/open-point/{point}/{index}", f"open-point/{point}", text,
                                  "open-point", protected))
    return found


def present(name: str, content: Content, rulebook_text: str) -> list[str]:
    """Where an invented name shows up in the Arbiter's sources: it must show up nowhere."""
    found = [card for card in content.card_names if name.lower() in card.lower() or card.lower() in name.lower()]
    found += [keyword for keyword in content.keywords if keyword.lower() == name.lower()]
    if re.search(rf"\b{re.escape(name)}\b", rulebook_text, re.IGNORECASE):
        found.append("the rulebook")
    return found


def still_open(open_points_text: str) -> list[str]:
    """The open points whose title is still under "Still open" in 12-open-points.md."""
    section = open_points_text.split("## Still open", 1)[1].split("\n## ", 1)[0]
    return [point for point, (title, _, _) in OPEN_POINTS.items() if f"**{title}.**" in section]


def _bare(name: str) -> str:
    """"a Shard Surge" → "Shard Surge": the name without the article the question needs."""
    return re.sub(r"^(a|an|the) ", "", name)


def _slug(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", _bare(name).lower()).strip("-")
