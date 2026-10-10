"""The dev / test split, by group and stratified by category (design doc §6.1).

A group sits on one side only: an interaction and all its scenarios, a card and all its factual questions, a rule,
a deck mutation, an invented name or an open point. A group keeps the side it had in the base version; only the new
groups are drawn, with a fixed seed, so that about 10% of each category's groups are in dev.
"""

import random
from collections import defaultdict

DEV_SHARE = 0.10
SEED = 1


Group = tuple[str, str]


def assign(entries: list[dict], seed: int, base: dict[Group, str] | None = None) -> dict[Group, str]:
    """(category, group) → "dev" or "test". A category with 2 groups or more has at least one in dev."""
    base = base or {}
    groups: dict[str, set[str]] = defaultdict(set)
    for entry in entries:
        groups[entry["category"]].add(entry["group"])
    sides: dict[Group, str] = {}
    for category in sorted(groups):
        names = sorted(groups[category])
        new = [name for name in names if (category, name) not in base]
        sides.update({(category, name): base[(category, name)] for name in names if (category, name) in base})
        target = max(1, round(DEV_SHARE * len(names))) if len(names) >= 2 else 0
        missing = target - sum(1 for name in names if base.get((category, name)) == "dev")
        random.Random(f"{seed}/{category}").shuffle(new)
        for index, name in enumerate(new):
            sides[(category, name)] = "dev" if index < missing else "test"
    return sides


def sides_of(entries: list[dict]) -> dict[Group, str]:
    """The side of each group of a built version, to carry it into the next one."""
    return {(entry["category"], entry["group"]): entry["split"] for entry in entries}


def leaks(entries: list[dict]) -> list[Group]:
    """The groups found on both sides of the split: there must be none."""
    sides: dict[Group, set[str]] = defaultdict(set)
    for entry in entries:
        sides[(entry["category"], entry["group"])].add(entry["split"])
    return sorted(group for group, found in sides.items() if len(found) > 1)
