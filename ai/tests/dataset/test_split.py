from collections import defaultdict

from dataset import split


def _entries():
    """Five categories of different sizes; each group holds three questions."""
    sizes = {"interaction": 20, "factual": 47, "multi-step": 5, "deck-building": 5, "lonely": 1}
    return [{"id": f"{category}/{group}/{index}", "category": category, "group": f"g{group}"}
            for category, count in sizes.items() for group in range(count) for index in range(3)]


def _split(entries, seed):
    sides = split.assign(entries, seed)
    return [{**entry, "split": sides[(entry["category"], entry["group"])]} for entry in entries]


def test_puts_every_question_of_a_group_on_the_same_side():
    assert split.leaks(_split(_entries(), 1)) == []


def test_finds_a_group_on_both_sides():
    entries = _split(_entries(), 1)
    entries[0] = {**entries[0], "split": "test" if entries[0]["split"] == "dev" else "dev"}

    assert split.leaks(entries) == [("interaction", entries[0]["group"])]


def test_sends_about_a_tenth_of_each_category_s_groups_to_dev():
    dev_groups = defaultdict(set)
    for entry in _split(_entries(), 1):
        if entry["split"] == "dev":
            dev_groups[entry["category"]].add(entry["group"])

    assert {category: len(groups) for category, groups in dev_groups.items()} == {
        "interaction": 2, "factual": 5, "multi-step": 1, "deck-building": 1}


def test_draws_the_same_split_from_the_same_seed():
    assert split.assign(_entries(), 1) == split.assign(_entries(), 1)
    assert split.assign(_entries(), 1) != split.assign(_entries(), 2)
