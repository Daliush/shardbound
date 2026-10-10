"""Checks every committed dataset version in evals/datasets/: what the split promises, read from the files."""

import json
from collections import Counter

import pytest

from dataset import jsonl, split
from dataset.paths import repo_root


def _versions():
    datasets = repo_root() / "evals" / "datasets"
    return sorted(folder for folder in datasets.glob("*") if (folder / "dataset.jsonl").exists()
                  and folder.name != "preview") if datasets.exists() else []


VERSIONS = _versions()
pytestmark = pytest.mark.skipif(not VERSIONS, reason="no dataset version is committed yet")


@pytest.fixture(params=VERSIONS, ids=lambda folder: folder.name)
def committed(request):
    folder = request.param
    return (jsonl.read(folder / "dataset.jsonl"), jsonl.read(folder / "hand-check.jsonl"),
            json.loads((folder / "manifest.json").read_text(encoding="utf-8")))


def test_no_group_sits_on_both_sides_of_the_split(committed):
    entries, _, _ = committed

    assert split.leaks(entries) == []
    assert {entry["split"] for entry in entries} == {"dev", "test"}


def test_every_category_has_dev_and_test_groups(committed):
    entries, _, _ = committed
    sides = {(entry["category"], entry["split"]) for entry in entries}

    assert all((category, side) in sides for category, _ in sides for side in ("dev", "test"))


def test_ids_are_unique_and_the_manifest_counts_them(committed):
    entries, _, manifest = committed
    per_category = Counter(entry["category"] for entry in entries)

    assert len({entry["id"] for entry in entries}) == len(entries) == manifest["counts"]["total"]
    assert per_category == {category: counts["total"]
                            for category, counts in manifest["counts"]["by_category"].items()}


def test_answer_keys_are_abstentions_exactly_for_unanswerable_questions(committed):
    entries, _, _ = committed

    for entry in entries:
        assert entry["answerable"] == (entry["category"] != "unanswerable"), entry["id"]
        assert bool(entry["key_facts"]) == entry["answerable"], entry["id"]


def test_the_hand_check_samples_the_dataset(committed):
    entries, sample, manifest = committed
    by_id = {entry["id"]: entry for entry in entries}

    assert len(sample) == manifest["hand_check"]["size"]
    assert all(row["question"] == by_id[row["id"]]["question"] for row in sample)
