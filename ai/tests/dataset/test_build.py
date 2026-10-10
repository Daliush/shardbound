import json

import pytest

from dataset import jsonl, split
from dataset.build import build
from dataset.llm import FakeClient

CATEGORIES = {"interaction", "multi-step", "factual", "deck-building", "simple-rule", "unanswerable"}


def _client(**replies):
    """The fake model; its rule questions fail their check for the rule about an empty deck (1.4)."""
    return FakeClient({
        "write-rule-question": lambda prompt: "What does this mean for my game?",
        "check-rule-question": lambda prompt: ('{"verdict": "fail", "reason": "too vague"}'
                                               if "empty deck" in prompt.user else '{"verdict": "pass"}'),
        **replies,
    })


def test_a_dry_run_writes_every_category_worded_split_and_counted(root, version):
    manifest = build(root, version, _client(), workers=2)
    entries = jsonl.read(version / "dataset.jsonl")

    assert {entry["category"] for entry in entries} == CATEGORIES
    assert split.leaks(entries) == []
    assert {entry["split"] for entry in entries} == {"dev", "test"}
    assert len({entry["id"] for entry in entries}) == len(entries) == manifest["counts"]["total"]
    assert manifest["model"] == "fake"
    assert manifest["counts"]["by_category"]["factual"]["total"] == 9
    assert all(entry["answerable"] == (entry["category"] != "unanswerable") for entry in entries)
    assert all(entry["key_facts"] for entry in entries if entry["answerable"])


def test_paraphrases_the_question_around_a_decklist_and_never_the_list(root, version):
    build(root, version, _client(), workers=2)
    deck = next(entry for entry in jsonl.read(version / "dataset.jsonl") if entry["category"] == "deck-building")
    decklist = next(row["decklist"] for row in jsonl.read(version / "decks.jsonl") if row["id"] == deck["id"])

    assert deck["question"].startswith("Quick rules question: ")
    assert deck["question"].endswith("\n\n" + decklist)
    assert deck["template_question"].endswith("\n\n" + decklist)
    assert deck["citations"] == deck["rules"]


def test_cites_the_card_of_a_factual_question_and_the_rule_of_a_simple_rule_question(root, version):
    build(root, version, _client(), workers=2)
    entries = {entry["id"]: entry for entry in jsonl.read(version / "dataset.jsonl")}

    assert entries["card/ember.ash-warden/cost"]["citations"] == ["ember.ash-warden"]
    assert entries["card/ember.ash-warden/cost"]["group"] == "ember.ash-warden"
    assert entries["rule/7.5"]["citations"] == ["7.5"]
    assert entries["rule/7.5"]["key_facts"][1] == "A unit can intercept at most once per turn."
    assert entries["rule/7.5"]["wording"] == "written"


def test_keeps_the_template_of_a_question_whose_rewrite_fails_and_says_so(root, version):
    fails_on_moonpull = lambda prompt: ('{"verdict": "fail", "reason": "lost a step"}' if "Moonpull" in prompt.user
                                        else '{"verdict": "pass"}')
    manifest = build(root, version, _client(**{"check-paraphrase": fails_on_moonpull}), workers=2)
    moonpull = [entry for entry in jsonl.read(version / "dataset.jsonl") if "tide.moonpull" in entry["id"]]

    assert moonpull and all(entry["question"] == entry["template_question"] for entry in moonpull)
    assert all(entry["wording"] == "template" for entry in moonpull)
    assert manifest["wording"]["template_kept"] == len(moonpull)
    assert {"id": "card/tide.moonpull/cost", "stage": "paraphrase", "outcome": "template kept",
            "reason": "model check: lost a step"} in manifest["failed_checks"]


def test_drops_a_simple_rule_question_that_fails_its_check(root, version):
    manifest = build(root, version, _client(), workers=2)
    ids = {entry["id"] for entry in jsonl.read(version / "dataset.jsonl")}

    assert "rule/1.4" not in ids
    assert {"id": "rule/1.4", "stage": "rule-question", "outcome": "dropped", "reason": "model check: too vague"} \
        in manifest["failed_checks"]
    assert manifest["wording"]["rule_questions_dropped"] >= 1


def test_samples_the_hand_check_from_the_dataset(root, version):
    build(root, version, _client(), workers=2)
    ids = {entry["id"] for entry in jsonl.read(version / "dataset.jsonl")}
    sample = jsonl.read(version / "hand-check.jsonl")

    assert sample and {row["id"] for row in sample} <= ids
    assert {row["category"] for row in sample} == CATEGORIES


def test_builds_the_same_dataset_from_the_same_inputs(root, version):
    build(root, version, _client(), workers=4)
    first = (version / "dataset.jsonl").read_bytes(), (version / "manifest.json").read_bytes()
    build(root, version, _client(), workers=1, overwrite=True)

    assert ((version / "dataset.jsonl").read_bytes(), (version / "manifest.json").read_bytes()) == first


def test_never_overwrites_a_built_version_by_accident(root, version):
    build(root, version, _client(), workers=2)

    with pytest.raises(SystemExit, match="a dataset version is frozen"):
        build(root, version, _client(), workers=2)


def test_needs_the_engine_s_files_first(root, version):
    (version / "decks.jsonl").unlink()

    with pytest.raises(SystemExit, match="Missing decks.jsonl"):
        build(root, version, _client(), workers=2)


def test_writes_a_manifest_that_says_how_the_dataset_was_made(root, version):
    manifest = json.loads(build(root, version, _client(), workers=2) and (version / "manifest.json").read_text(
        encoding="utf-8"))

    assert manifest["version"] == "v-test"
    assert manifest["seed"] == 1
    assert len(manifest["prompts_sha256"]) == len(manifest["content_sha256"]) == 64
    assert set(manifest["inputs_sha256"]) == {"scenarios.jsonl", "cards.jsonl", "decks.jsonl"}
    assert sum(category["total"] for category in manifest["counts"]["by_category"].values()) \
        == manifest["counts"]["total"]
