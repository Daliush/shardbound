"""What the Arbiter's sources name: the cards of `content/cards/` and the keywords of the rulebook."""

import json
import re
from dataclasses import dataclass
from pathlib import Path

_KEYWORD_FILE = re.compile(r"^11-\d+-([a-z]+)\.md$")


@dataclass(frozen=True)
class Content:
    card_names: tuple[str, ...]
    keywords: tuple[str, ...]

    def names_in(self, text: str) -> list[str]:
        """The card names and keywords a text mentions, the longest first so "Root Sentinel" wins over "Root"."""
        terms = sorted({*self.card_names, *self.keywords}, key=len, reverse=True)
        return [term for term in terms if re.search(rf"\b{re.escape(term)}\b", text)]


def load(root: Path) -> Content:
    names = []
    for file in sorted((root / "content" / "cards").glob("*/*.json")):
        names.append(json.loads(file.read_text(encoding="utf-8"))["name"])
    keywords = [match.group(1).capitalize() for file in sorted((root / "docs" / "rules").glob("*.md"))
                if (match := _KEYWORD_FILE.match(file.name))]
    return Content(tuple(names), tuple(keywords))
