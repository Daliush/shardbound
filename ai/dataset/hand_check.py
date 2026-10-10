"""The sample checked by hand (design doc §6.1): about 150 questions, stratified by category and spread over groups.

A reviewer reads each question against the rulebook and the card data, and fills `review`.
"""

import random
from collections import defaultdict

SIZE = 150
MINIMUM = 10


def sample(entries: list[dict], size: int, seed: int) -> list[dict]:
    by_category: dict[str, list[dict]] = defaultdict(list)
    for entry in entries:
        by_category[entry["category"]].append(entry)
    quotas = _quotas({category: len(found) for category, found in by_category.items()}, size)
    rows = []
    for category in sorted(by_category):
        chosen = _spread(by_category[category], quotas[category], random.Random(f"{seed}/hand-check/{category}"))
        rows.extend(_row(entry) for entry in chosen)
    return rows


def _quotas(sizes: dict[str, int], size: int) -> dict[str, int]:
    """Proportional to each category's size, at least MINIMUM each (or the whole category), summing to `size`."""
    total = sum(sizes.values())
    share = {category: size * count / total for category, count in sizes.items()}
    quotas = {category: min(count, max(MINIMUM, int(share[category]))) for category, count in sizes.items()}
    while sum(quotas.values()) < min(size, total):
        room = [category for category in sizes if quotas[category] < sizes[category]]
        quotas[max(room, key=lambda category: (share[category] - quotas[category], category))] += 1
    while sum(quotas.values()) > size:
        above = [category for category in sizes if quotas[category] > MINIMUM]
        quotas[max(above, key=lambda category: (quotas[category] - share[category], category))] -= 1
    return quotas


def _spread(entries: list[dict], count: int, generator: random.Random) -> list[dict]:
    """One entry per group in turn, so the sample covers as many interactions, cards or rules as it can."""
    groups: dict[str, list[dict]] = defaultdict(list)
    for entry in entries:
        groups[entry["group"]].append(entry)
    order = sorted(groups)
    generator.shuffle(order)
    for name in order:
        generator.shuffle(groups[name])
    chosen: list[dict] = []
    while len(chosen) < count:
        for name in order:
            if groups[name] and len(chosen) < count:
                chosen.append(groups[name].pop())
    return chosen


def _row(entry: dict) -> dict:
    return {
        "id": entry["id"],
        "category": entry["category"],
        "split": entry["split"],
        "question": entry["question"],
        "template_question": entry["template_question"],
        "answerable": entry["answerable"],
        "key_facts": entry["key_facts"],
        "citations": entry["citations"],
        "review": {"question": None, "answer_key": None, "notes": ""},
    }
