"""Builds a dataset version: every category, worded, split, with its hand-check sample and its manifest.

The engine's files come first (`scenarios.jsonl`, `cards.jsonl`, `decks.jsonl`, from `engine/dataset`); this adds
the unanswerable and simple-rule questions, words every question, splits by group and writes `dataset.jsonl`,
`hand-check.jsonl` and `manifest.json` next to them.
"""

import hashlib
import json
import subprocess
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from dataset import content as content_module
from dataset import hand_check, jsonl, prompts, rulebook, simple_rules, split, unanswerable
from dataset.content import Content
from dataset.llm import LlmClient
from dataset.wording import word

SEED = 1
ENGINE_FILES = ("scenarios.jsonl", "cards.jsonl", "decks.jsonl")
OUTPUT_FILES = ("dataset.jsonl", "hand-check.jsonl", "manifest.json")


def build(root: Path, folder: Path, client: LlmClient, workers: int = 8, overwrite: bool = False) -> dict:
    """Builds the version in `folder` (`evals/datasets/<version>/`) from the content and rulebook under `root`."""
    missing = [name for name in ENGINE_FILES if not (folder / name).exists()]
    if missing:
        raise SystemExit(f"Missing {', '.join(missing)} in {folder}: run the engine's generator first "
                         f"(engine/: ./mvnw -q -pl dataset -am compile exec:java -Dexec.args={folder.name}).")
    if (folder / "dataset.jsonl").exists() and not overwrite:
        raise SystemExit(f"{folder / 'dataset.jsonl'} exists: a dataset version is frozen. Pass --overwrite to "
                         "build it again.")
    content = content_module.load(root)
    rules = rulebook.load(root / "docs" / "rules")
    failures: list[dict] = []
    with ThreadPoolExecutor(max_workers=workers) as pool:
        rule_questions = rule_entries(rules, content, client, pool, failures)
        entries = engine_entries(folder) + rule_questions + unanswerable_entries()
        word_all([entry for entry in entries if entry["wording"] == "pending"], content, client, pool, failures)
    sides = split.assign(entries, SEED)
    for entry in entries:
        entry["split"] = sides[(entry["category"], entry["group"])]
    entries = [_ordered(entry) for entry in entries]
    sample = hand_check.sample(entries, hand_check.SIZE, SEED)
    jsonl.write(folder / "dataset.jsonl", entries)
    jsonl.write(folder / "hand-check.jsonl", sample)
    manifest = _manifest(root, folder, client, entries, sample, failures)
    (folder / "manifest.json").write_text(_json(manifest), encoding="utf-8", newline="\n")
    return manifest


def engine_entries(folder: Path) -> list[dict]:
    """The engine's three files, in the dataset's shape; the decklist is appended to a question after wording."""
    entries = []
    for scenario in jsonl.read(folder / "scenarios.jsonl"):
        entries.append(_entry(scenario, group=scenario["interaction"], source="engine",
                              citations=_unique(scenario["rules"] + scenario["context_rules"])))
    for card in jsonl.read(folder / "cards.jsonl"):
        entries.append(_entry(card, group=card["card"], source="card-data", citations=[card["card"]],
                              cards=[card["card"]]))
    for deck in jsonl.read(folder / "decks.jsonl"):
        entries.append(_entry(deck, group=deck["mutation"], source="engine", citations=deck["rules"],
                              decklist=deck["decklist"]))
    return entries


def unanswerable_entries() -> list[dict]:
    return [{"id": question.id, "category": "unanswerable", "group": question.group, "question": question.question,
             "template_question": question.question, "wording": "pending", "protected": list(question.protected),
             "answerable": False, "source": question.source, "key_facts": [], "citations": [], "rules": [],
             "context_rules": [], "cards": [], "trace": []}
            for question in unanswerable.questions()]


def rule_entries(rules: list[rulebook.Rule], content: Content, client: LlmClient, pool: ThreadPoolExecutor,
                 failures: list[dict]) -> list[dict]:
    entries = []
    for asked in pool.map(lambda rule: simple_rules.ask(rule, content, client), simple_rules.selected(rules)):
        rule = asked.rule
        if asked.question is None:
            failures.append({"id": f"rule/{rule.id}", "stage": "rule-question", "outcome": "dropped",
                             "reason": asked.failure})
            continue
        entries.append({"id": f"rule/{rule.id}", "category": "simple-rule", "group": rule.id,
                        "question": asked.question, "template_question": asked.question, "wording": "written",
                        "answerable": True, "source": "rulebook", "key_facts": list(rule.facts),
                        "citations": [rule.id], "rules": [rule.id], "context_rules": [], "cards": [], "trace": []})
    return entries


def word_all(entries: list[dict], content: Content, client: LlmClient, pool: ThreadPoolExecutor,
             failures: list[dict]) -> None:
    """Words each entry's template in place; a deck question is worded without its decklist, then gets it back."""
    worded = pool.map(lambda entry: word(entry["template_question"], entry.get("protected", []), content, client),
                      entries)
    for entry, wording in zip(entries, worded):
        entry["question"] = wording.question
        entry["wording"] = wording.status
        if wording.failure:
            failures.append({"id": entry["id"], "stage": "paraphrase", "outcome": "template kept",
                             "reason": wording.failure})
        if "decklist" in entry:
            entry["template_question"] += "\n\n" + entry["decklist"]
            entry["question"] += "\n\n" + entry["decklist"]


def _entry(row: dict, group: str, source: str, citations: list[str], cards: list[str] | None = None,
           decklist: str | None = None) -> dict:
    entry = {"id": row["id"], "category": row["category"], "group": group, "question": row["question"],
             "template_question": row["question"], "wording": "pending", "answerable": True, "source": source,
             "key_facts": row["key_facts"], "citations": citations, "rules": row.get("rules", []),
             "context_rules": row.get("context_rules", []), "cards": cards or row.get("cards", []),
             "trace": row.get("trace", [])}
    if decklist is not None:
        entry["decklist"] = decklist
    return entry


_FIELDS = ("id", "category", "group", "split", "question", "template_question", "wording", "answerable", "source",
           "key_facts", "citations", "rules", "context_rules", "cards", "trace")


def _ordered(entry: dict) -> dict:
    """The dataset's fields in a fixed order; the working fields (decklist, protected names) are dropped."""
    return {field: entry[field] for field in _FIELDS}


def _manifest(root: Path, folder: Path, client: LlmClient, entries: list[dict], sample: list[dict],
              failures: list[dict]) -> dict:
    by_category: dict[str, Counter] = defaultdict(Counter)
    groups: dict[str, dict[str, set]] = defaultdict(lambda: defaultdict(set))
    for entry in entries:
        by_category[entry["category"]][entry["split"]] += 1
        groups[entry["category"]][entry["split"]].add(entry["group"])
    wording = Counter(entry["wording"] for entry in entries)
    return {
        "version": folder.name,
        "seed": SEED,
        "dev_share": split.DEV_SHARE,
        "model": client.model,
        "model_options": client.options,
        "prompts_sha256": prompts.fingerprint(),
        "engine_commit": _git(root, "rev-parse", "HEAD"),
        "sources_clean": _git(root, "status", "--porcelain", "--", "engine", "content", "docs/rules", "ai") == "",
        "content_sha256": _tree_hash(root / "content", ("cards", "decks")),
        "rulebook_sha256": _tree_hash(root / "docs", ("rules",)),
        "inputs_sha256": {name: _file_hash(folder / name) for name in ENGINE_FILES},
        "counts": {
            "total": len(entries),
            "by_split": dict(sorted(Counter(entry["split"] for entry in entries).items())),
            "by_category": {category: {"dev": counts["dev"], "test": counts["test"], "total": sum(counts.values())}
                            for category, counts in sorted(by_category.items())},
            "groups": {category: {side: len(names) for side, names in sorted(sides.items())}
                       for category, sides in sorted(groups.items())},
        },
        "wording": {
            "policy": "A paraphrase that fails its check keeps its template; a simple-rule question that fails its "
                      "check is dropped, since it has no template.",
            "paraphrased": wording["paraphrase"],
            "template_kept": wording["template"],
            "written_by_model": wording["written"],
            "rule_questions_dropped": sum(1 for failure in failures if failure["outcome"] == "dropped"),
        },
        "failed_checks": sorted(failures, key=lambda failure: failure["id"]),
        "hand_check": {"size": len(sample),
                       "by_category": dict(sorted(Counter(row["category"] for row in sample).items()))},
    }


def _unique(items: list[str]) -> list[str]:
    return list(dict.fromkeys(items))


def _git(root: Path, *args: str) -> str:
    try:
        return subprocess.run(["git", *args], cwd=root, capture_output=True, text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def _tree_hash(base: Path, folders: tuple[str, ...]) -> str:
    """Line endings are normalized, so a checkout on Windows hashes like one on Linux."""
    digest = hashlib.sha256()
    for folder in folders:
        for file in sorted((base / folder).rglob("*")):
            if file.is_file():
                digest.update(file.relative_to(base).as_posix().encode("utf-8") + b"\0")
                digest.update(file.read_bytes().replace(b"\r\n", b"\n") + b"\0")
    return digest.hexdigest()


def _file_hash(file: Path) -> str:
    return hashlib.sha256(file.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def _json(value: dict) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"
