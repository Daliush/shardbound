from collections import defaultdict

from dataset import split


def _entries(sizes=None):
    """Categories of different sizes; each group holds three questions."""
    sizes = sizes or {"interaction": 20, "factual": 47, "multi-step": 5, "deck-building": 5, "lonely": 1}
    return [{"id": f"{category}/{group}/{index}", "category": category, "group": f"g{group:02}"}
            for category, count in sizes.items() for group in range(count) for index in range(3)]


def _split(entries, seed=1, base=None):
    sides = split.assign(entries, seed, base)
    return [{**entry, "split": sides[(entry["category"], entry["group"])]} for entry in entries]


def _dev_groups(entries):
    dev = defaultdict(set)
    for entry in entries:
        if entry["split"] == "dev":
            dev[entry["category"]].add(entry["group"])
    return dev


def test_puts_every_question_of_a_group_on_the_same_side():
    assert split.leaks(_split(_entries())) == []


def test_finds_a_group_on_both_sides():
    entries = _split(_entries())
    entries[0] = {**entries[0], "split": "test" if entries[0]["split"] == "dev" else "dev"}

    assert split.leaks(entries) == [("interaction", entries[0]["group"])]


def test_sends_about_a_tenth_of_each_category_s_groups_to_dev():
    dev = _dev_groups(_split(_entries()))

    assert {category: len(groups) for category, groups in dev.items()} == {
        "interaction": 2, "factual": 5, "multi-step": 1, "deck-building": 1}


def test_draws_the_same_split_from_the_same_seed():
    assert split.assign(_entries(), 1) == split.assign(_entries(), 1)
    assert split.assign(_entries(), 1) != split.assign(_entries(), 2)


def test_keeps_the_side_of_every_group_the_base_version_has():
    base = _split(_entries())
    grown = _entries({"interaction": 30, "factual": 47, "multi-step": 5, "deck-building": 5, "lonely": 1})

    following = _split(grown, base=split.sides_of(base))
    sides = split.sides_of(following)

    assert all(sides[group] == side for group, side in split.sides_of(base).items())
    assert len(_dev_groups(following)["interaction"]) == 3
