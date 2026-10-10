"""The rulebook of `docs/rules/`: each rule with its id, its section and its own sentences."""

import re
from dataclasses import dataclass
from pathlib import Path

# A rule starts a bullet or a numbered item: "- **7.5** ..." or "1. **5.2.1** ...".
_RULE = re.compile(r"^\s*(?:-|\d+\.)\s+\*\*(\d+\.\d+(?:\.\d+)?)\*\*\s*(.*)$")
_ITEM = re.compile(r"^\s+(?:-|\d+\.)\s+(.*)$")
_TITLE = re.compile(r"^#\s+(\d+(?:\.\d+)?)\.?\s+(.*)$")
_LABEL = re.compile(r"^\*\*([^*]+\.)\*\*\s*")
_SENTENCE_END = re.compile(r"(?:(?<=[.!?])|(?<=[.!?]\"))\s+(?=[A-Z\"(])")
# Abbreviations hide their periods from the sentence split behind a look-alike dot (U+2024).
_ABBREVIATIONS = {"e.g.": "e․g․", "i.e.": "i․e․"}


@dataclass(frozen=True)
class Rule:
    id: str
    section: str
    title: str
    text: str
    facts: tuple[str, ...]

    @property
    def section_number(self) -> int:
        return int(self.id.split(".")[0])


def load(rules_dir: Path) -> list[Rule]:
    """Every rule of every file, in file order; rules marked removed are left out."""
    rules: list[Rule] = []
    for file in sorted(rules_dir.glob("*.md")):
        rules.extend(_rules_of(file.read_text(encoding="utf-8")))
    return [rule for rule in rules if "(removed" not in rule.text]


def text(rules_dir: Path) -> str:
    """The whole rulebook, to check that an invented name appears nowhere in it."""
    return "\n".join(file.read_text(encoding="utf-8") for file in sorted(rules_dir.glob("*.md")))


def _rules_of(markdown: str) -> list[Rule]:
    section, title = "", ""
    rules: list[Rule] = []
    current: list[str] | None = None
    current_id = ""
    for line in markdown.splitlines():
        heading = _TITLE.match(line)
        if heading:
            section, title = heading.group(1), heading.group(2).strip()
            continue
        start = _RULE.match(line)
        if start:
            if current is not None:
                rules.append(_rule(current_id, section, title, current))
            current_id, current = start.group(1), [start.group(2)]
        elif current is not None and (line.startswith(" ") or not line.strip()):
            current.append(line)
        elif current is not None:
            rules.append(_rule(current_id, section, title, current))
            current = None
    if current is not None:
        rules.append(_rule(current_id, section, title, current))
    return rules


def _rule(rule_id: str, section: str, title: str, lines: list[str]) -> Rule:
    """Examples (quoted blocks) explain a rule but are not part of it, so they are left out."""
    kept = [line for line in lines if not line.strip().startswith(">")]
    label = _LABEL.match(kept[0])
    lead = _sentences(_plain(kept[0][label.end():] if label else kept[0]))
    # "A unit can attack if all of the following hold:" introduces the items after it.
    introducer = lead.pop()[:-1] if lead and lead[-1].endswith(":") else None
    items = [_plain(item.group(1)) for item in map(_ITEM.match, kept[1:]) if item]
    paragraphs = [_plain(line) for line in kept[1:] if line.strip() and not _ITEM.match(line)]
    facts = list(lead)
    if introducer and all(item[:1].isupper() for item in items):
        facts.append(_ended(introducer))
    for item in items:
        facts.append(_ended(f"{introducer}: {item}" if introducer and not item[:1].isupper() else item))
    for paragraph in paragraphs:
        facts.extend(_sentences(paragraph))
    full = " ".join(_plain(line) for line in kept if line.strip())
    return Rule(rule_id, section, title, full, tuple(facts))


def _plain(markdown: str) -> str:
    return markdown.replace("**", "").replace("*", "").strip()


def _sentences(paragraph: str) -> list[str]:
    protected = paragraph
    for abbreviation, stand_in in _ABBREVIATIONS.items():
        protected = protected.replace(abbreviation, stand_in)
    # A quoted card text ("Deal 4 damage to the target. Draw 1 card.") stays in its sentence.
    cuts = [end.start() for end in _SENTENCE_END.finditer(protected) if protected[:end.start()].count('"') % 2 == 0]
    bounds = zip([0, *cuts], [*cuts, len(protected)])
    sentences = [protected[start:end].strip() for start, end in bounds if protected[start:end].strip()]
    restored = []
    for sentence in sentences:
        for abbreviation, stand_in in _ABBREVIATIONS.items():
            sentence = sentence.replace(stand_in, abbreviation)
        restored.append(_ended(sentence))
    return restored


def _ended(sentence: str) -> str:
    """A list item ends with ";" or nothing; as a key fact on its own, it ends with a period."""
    sentence = sentence.rstrip(" ;")
    return sentence if sentence.rstrip('"').endswith((".", "!", "?", ":")) else sentence + "."
