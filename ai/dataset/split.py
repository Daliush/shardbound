"""The dev / test split, by group and stratified by category (design doc §6.1).

A group sits on one side only: an interaction and all its scenarios, a card and all its factual questions, a rule,
a deck mutation, an invented name or an open point. About 10% of each category's groups go to dev, drawn with a
fixed seed; the rest is test. The split is written into each entry and never recomputed.
"""

import random
from collections import defaultdict

DEV_SHARE = 0.10


def assign(entries: list[dict], seed: int) -> dict[tuple[str, str], str]:
    """(category, group) → "dev" or "test". A category with 2 groups or more gets at least one in dev."""
    groups: dict[str, set[str]] = defaultdict(set)
    for entry in entries:
        groups[entry["category"]].add(entry["group"])
    sides: dict[tuple[str, str], str] = {}
    for category in sorted(groups):
        names = sorted(groups[category])
        random.Random(f"{seed}/{category}").shuffle(names)
        dev_count = max(1, round(DEV_SHARE * len(names))) if len(names) >= 2 else 0
        for index, name in enumerate(names):
            sides[(category, name)] = "dev" if index < dev_count else "test"
    return sides


def leaks(entries: list[dict]) -> list[tuple[str, str]]:
    """The groups found on both sides of the split: there must be none."""
    sides: dict[tuple[str, str], set[str]] = defaultdict(set)
    for entry in entries:
        sides[(entry["category"], entry["group"])].add(entry["split"])
    return sorted(group for group, found in sides.items() if len(found) > 1)
