import json
import shutil

import pytest

from dataset import jsonl, split
from dataset.build import DATASETS, build, read
from dataset.llm import FakeClient, ModelApiError

CATEGORIES = {"interaction", "multi-step", "factual", "deck-building", "simple-rule", "unanswerable"}


def test_words_and_splits_every_question_of_the_engine_s_files(root, version):
    manifest = build(root, version, FakeClient(), workers=2)
    entries = read(version)

    assert {entry["category"] for entry in entries} == CATEGORIES
    assert all(entry["question"].startswith("Quick rules question: ") for entry in entries)
    assert split.leaks(entries) == []
    assert {entry["split"] for entry in entries} == {"dev", "test"}
    assert len({entry["id"] for entry in entries}) == len(entries) == manifest["counts"]["total"]
    assert {key: value for key, value in manifest["wording"].items() if key != "policy"} == {
        "paraphrase": len(entries), "template": 0, "written": 0, "reused_now": 0, "reworded_now": len(entries)}


def test_writes_the_dev_and_the_test_questions_into_two_datasets(root, version):
    build(root, version, FakeClient(), workers=2)

    for side, name in DATASETS.items():
        assert {entry["split"] for entry in jsonl.read(version / name)} == {side}


def test_carries_each_answer_key_through_untouched(root, version):
    build(root, version, FakeClient(), workers=2)
    entries = {entry["id"]: entry for entry in read(version)}

    assert entries["card/ember.ash-warden/cost"]["citations"] == ["ember.ash-warden"]
    assert entries["card/ember.ash-warden/cost"]["group"] == "ember.ash-warden"
    assert entries["rule/7.5"]["citations"] == entries["rule/7.5"]["rules"] == ["7.5"]
    assert "A frozen unit cannot intercept." in entries["rule/7.5"]["key_facts"]
    unanswerable = entries["unanswerable/keyword/siphon/1"]
    assert (unanswerable["answerable"], unanswerable["key_facts"], unanswerable["source"]) == (False, [], "invented")


def test_rewords_a_deck_question_around_its_decklist_which_the_model_never_sees(root, version):
    client = FakeClient()
    build(root, version, client, workers=2)
    deck = next(entry for entry in read(version) if entry["category"] == "deck-building")
    decklist = next(row["decklist"] for row in jsonl.read(version / "decks.jsonl") if row["id"] == deck["id"])

    assert deck["question"].endswith("\n\n" + decklist)
    assert deck["template_question"].endswith("\n\n" + decklist)
    assert not any("Faction:" in call.user for call in client.calls)


def test_keeps_the_wording_and_the_side_of_every_question_unchanged_since_the_base(root, version, tmp_path):
    build(root, version, FakeClient(), workers=2)
    following = tmp_path / "v-next"
    _copy_unbuilt(version, following)
    cards = jsonl.read(following / "cards.jsonl")
    cards[0]["question"] = "How many Shards does Ash Warden cost to play?"
    jsonl.write(following / "cards.jsonl", cards)
    client = FakeClient({"paraphrase": lambda prompt: "Reworded again: " + prompt.user.split("<question>")[1]
                        .split("</question>")[0]})

    manifest = build(root, following, client, base=version, workers=2)
    before = {entry["id"]: entry for entry in read(version)}
    after = {entry["id"]: entry for entry in read(following)}

    assert after[cards[0]["id"]]["question"] == "Reworded again: How many Shards does Ash Warden cost to play?"
    assert all(after[id_]["question"] == before[id_]["question"] for id_ in after if id_ != cards[0]["id"])
    assert all(after[id_]["split"] == before[id_]["split"] for id_ in after)
    assert manifest["base"] == "v-test"
    assert manifest["wording"]["reused_now"] == len(after) - 1
    assert [call.task for call in client.calls] == ["paraphrase", "check-paraphrase"]


def test_draws_a_side_only_for_a_group_the_base_does_not_have(root, version, tmp_path):
    build(root, version, FakeClient(), workers=2)
    following = tmp_path / "v-next"
    _copy_unbuilt(version, following)
    scenarios = jsonl.read(following / "scenarios.jsonl")
    jsonl.write(following / "scenarios.jsonl", scenarios + [{**scenarios[0], "id": "new+interaction/1",
                                                             "interaction": "new+interaction"}])

    build(root, following, FakeClient(), base=version, workers=2)
    after = {entry["id"]: entry for entry in read(following)}

    assert after["new+interaction/1"]["split"] in ("dev", "test")
    assert split.leaks(list(after.values())) == []


def test_keeps_the_template_of_a_question_whose_rewrite_fails_and_says_so(root, version):
    fails_on_moonpull = lambda prompt: ('{"verdict": "fail", "reason": "lost a step"}' if "Moonpull" in prompt.user
                                        else '{"verdict": "pass"}')
    manifest = build(root, version, FakeClient({"check-paraphrase": fails_on_moonpull}), workers=2)
    moonpull = [entry for entry in read(version) if "tide.moonpull" in entry["id"]]

    assert moonpull and all(entry["question"] == entry["template_question"] for entry in moonpull)
    assert all(entry["wording"] == "template" for entry in moonpull)
    assert manifest["wording"]["template"] == len(moonpull)
    assert {"id": "card/tide.moonpull/cost", "outcome": "template kept",
            "reason": "model check: lost a step"} in manifest["failed_checks"]


def test_builds_the_same_dataset_from_the_same_inputs(root, version):
    build(root, version, FakeClient(), workers=4)
    first = [(version / name).read_bytes() for name in (*DATASETS.values(), "manifest.json")]
    build(root, version, FakeClient(), workers=1, overwrite=True)

    assert [(version / name).read_bytes() for name in (*DATASETS.values(), "manifest.json")] == first


def test_never_overwrites_a_built_version_by_accident(root, version):
    build(root, version, FakeClient(), workers=2)

    with pytest.raises(SystemExit, match="a dataset version is frozen"):
        build(root, version, FakeClient(), workers=2)


def test_needs_every_file_of_the_engine_first(root, version):
    (version / "rules.jsonl").unlink()

    with pytest.raises(SystemExit, match="Missing rules.jsonl"):
        build(root, version, FakeClient(), workers=2)


def test_stops_calling_the_model_once_a_call_is_refused(root, version):
    def refuse(prompt):
        raise ModelApiError("The model API answered 400: temperature")

    client = FakeClient({"paraphrase": refuse})

    with pytest.raises(ModelApiError):
        build(root, version, client, workers=1)
    assert len(client.calls) < 5
    assert not any((version / name).exists() for name in DATASETS.values())


def test_writes_a_manifest_that_says_how_the_dataset_was_made(root, version):
    build(root, version, FakeClient(), workers=2)
    manifest = json.loads((version / "manifest.json").read_text(encoding="utf-8"))

    assert (manifest["version"], manifest["base"], manifest["seed"], manifest["model"]) == ("v-test", None, 1, "fake")
    assert len(manifest["prompts_sha256"]) == 64
    assert set(manifest["inputs_sha256"]) == {"scenarios.jsonl", "cards.jsonl", "decks.jsonl", "rules.jsonl",
                                              "unanswerable.jsonl"}
    assert sum(category["total"] for category in manifest["counts"]["by_category"].values()) \
        == manifest["counts"]["total"]



def test_keeps_the_template_of_a_rewording_a_review_rejected(root, version):
    rejected = "card/ember.ash-warden/cost"
    (version / "kept-templates.json").write_text(json.dumps({rejected: "narrows the question"}), encoding="utf-8")

    manifest = build(root, version, FakeClient(), workers=2)
    entry = next(entry for entry in read(version) if entry["id"] == rejected)

    assert (entry["question"], entry["wording"]) == (entry["template_question"], "template")
    assert manifest["failed_checks"] == [{"id": rejected, "outcome": "rejected by review",
                                          "reason": "narrows the question"}]
    assert manifest["wording"]["reworded_now"] == manifest["counts"]["total"] - 1


def test_refuses_a_review_about_a_question_the_version_does_not_have(root, version):
    (version / "kept-templates.json").write_text(json.dumps({"card/nothing": "?"}), encoding="utf-8")

    with pytest.raises(SystemExit, match="names questions the version does not have: card/nothing"):
        build(root, version, FakeClient(), workers=2)


def test_carries_why_a_question_kept_its_template_into_the_next_version(root, version, tmp_path):
    fails_on_moonpull = lambda prompt: ('{"verdict": "fail", "reason": "lost a step"}' if "Moonpull" in prompt.user
                                        else '{"verdict": "pass"}')
    build(root, version, FakeClient({"check-paraphrase": fails_on_moonpull}), workers=2)
    following = tmp_path / "v-next"
    _copy_unbuilt(version, following)

    manifest = build(root, following, FakeClient(), base=version, workers=2)

    assert manifest["wording"]["reworded_now"] == 0
    assert {"id": "card/tide.moonpull/cost", "outcome": "template kept",
            "reason": "model check: lost a step"} in manifest["failed_checks"]
    assert len(manifest["failed_checks"]) == manifest["wording"]["template"]


def test_a_version_rebuilt_on_itself_keeps_its_wordings_and_has_no_base(root, version):
    build(root, version, FakeClient(), workers=2)
    before = read(version)

    manifest = build(root, version, FakeClient({"paraphrase": lambda prompt: "never asked"}), base=version,
                     workers=2, overwrite=True)

    assert read(version) == before
    assert manifest["base"] is None
    assert manifest["wording"]["reused_now"] == len(before)


def test_refreshes_the_hand_check_sample_and_keeps_its_reviews(root, version):
    build(root, version, FakeClient(), workers=2)
    review = {"question": "ok", "answer_key": "ok", "notes": "checked"}
    jsonl.write(version / "hand-check.jsonl", [{"id": "rule/7.5", "question": "old", "key_facts": [],
                                                "review": review}])
    rules = jsonl.read(version / "rules.jsonl")
    jsonl.write(version / "rules.jsonl", [{**row, "key_facts": ["New fact."]} if row["rule"] == "7.5" else row
                                          for row in rules])

    build(root, version, FakeClient(), workers=2, overwrite=True)
    row = jsonl.read(version / "hand-check.jsonl")[0]

    assert row["key_facts"] == ["New fact."]
    assert row["question"] == next(entry["question"] for entry in read(version) if entry["id"] == "rule/7.5")
    assert row["review"] == review

def _copy_unbuilt(version, following):
    """A copy of a built version holding only the engine's files, as the next version starts."""
    shutil.copytree(version, following)
    for name in DATASETS.values():
        (following / name).unlink()
