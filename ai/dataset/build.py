"""Words and splits a dataset version.

`engine/dataset` writes every question of a version with its answer key: `scenarios.jsonl`, `cards.jsonl`,
`decks.jsonl`, `rules.jsonl` and `unanswerable.jsonl`. This rewords the questions the way a player would ask them,
splits them into dev and test, and writes the two final datasets, `dataset-dev.jsonl` and `dataset-test.jsonl`, with
`manifest.json` next to them.

Nothing is worded or drawn twice. A question whose template has not changed since the base version (the previous
one) keeps its wording, and a group keeps its side: only new questions go to the model, and only new groups are drawn.
"""

import hashlib
import json
import subprocess
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from dataset import content as content_module
from dataset import jsonl, prompts, split
from dataset.content import Content
from dataset.llm import LlmClient
from dataset.wording import word

ENGINE_FILES = ("scenarios.jsonl", "cards.jsonl", "decks.jsonl", "rules.jsonl", "unanswerable.jsonl")
DATASETS = {"dev": "dataset-dev.jsonl", "test": "dataset-test.jsonl"}


def build(root: Path, folder: Path, client: LlmClient, base: Path | None = None, workers: int = 8,
          overwrite: bool = False) -> dict:
    """Builds the version in `folder` (`evals/datasets/<version>/`), keeping what `base` already worded and split."""
    missing = [name for name in ENGINE_FILES if not (folder / name).exists()]
    if missing:
        raise SystemExit(f"Missing {', '.join(missing)} in {folder}: run the engine's generator first "
                         f"(engine/: ./mvnw -q -pl dataset -am compile exec:java -Dexec.args={folder.name}).")
    if is_built(folder) and not overwrite:
        raise SystemExit(f"{folder} is built: a dataset version is frozen. Pass --overwrite to build it again.")
    previous = {entry["id"]: entry for entry in read(base)} if base else {}
    entries = questions(folder)
    failures: list[dict] = []
    reused = word_all(entries, previous, content_module.load(root), client, workers, failures)
    sides = split.assign(entries, split.SEED, split.sides_of(list(previous.values())))
    for entry in entries:
        entry["split"] = sides[(entry["category"], entry["group"])]
    entries = [_ordered(entry) for entry in entries]
    for side, name in DATASETS.items():
        jsonl.write(folder / name, [entry for entry in entries if entry["split"] == side])
    manifest = _manifest(root, folder, base, client, entries, reused, failures)
    (folder / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
                                          encoding="utf-8", newline="\n")
    return manifest


def is_built(folder: Path) -> bool:
    return any((folder / name).exists() for name in DATASETS.values())


def read(folder: Path) -> list[dict]:
    """Both final datasets of a built version, dev then test."""
    if not is_built(folder):
        raise SystemExit(f"{folder} has no dataset-dev.jsonl nor dataset-test.jsonl: it is not built.")
    return [entry for name in DATASETS.values() for entry in jsonl.read(folder / name)]


def questions(folder: Path) -> list[dict]:
    """The engine's files in the dataset's shape: the group that decides the split, and the essential citations."""
    entries = []
    for row in jsonl.read(folder / "scenarios.jsonl"):
        entries.append(_entry(row, group=row["interaction"], source="engine",
                              citations=list(dict.fromkeys(row["rules"] + row["context_rules"]))))
    for row in jsonl.read(folder / "cards.jsonl"):
        entries.append(_entry(row, group=row["card"], source="card-data", citations=[row["card"]],
                              cards=[row["card"]]))
    for row in jsonl.read(folder / "decks.jsonl"):
        entries.append(_entry(row, group=row["mutation"], source="engine", citations=row["rules"],
                              decklist=row["decklist"]))
    for row in jsonl.read(folder / "rules.jsonl"):
        entries.append(_entry(row, group=row["rule"], source="rulebook", citations=[row["rule"]],
                              rules=[row["rule"]]))
    for row in jsonl.read(folder / "unanswerable.jsonl"):
        entries.append(_entry({**row, "key_facts": []}, group=row["group"], source=row["source"], citations=[],
                              answerable=False, protected=row["protected_terms"]))
    return entries


def word_all(entries: list[dict], previous: dict[str, dict], content: Content, client: LlmClient, workers: int,
             failures: list[dict]) -> int:
    """Gives each entry its `question`: the base version's wording if its template is unchanged, else a new one.

    A deck question is reworded without its decklist, which is appended afterwards. Returns how many were reused.
    """
    new = []
    for entry in entries:
        old = previous.get(entry["id"])
        if old and old["template_question"] == entry["template_question"]:
            entry["question"], entry["wording"] = old["question"], old["wording"]
        else:
            new.append(entry)
    with ThreadPoolExecutor(max_workers=workers) as pool:
        worded = pool.map(lambda entry: word(entry["reword"], entry["protected"], content, client), new)
        for entry, wording in zip(new, worded):
            entry["question"] = wording.question + entry["suffix"]
            entry["wording"] = wording.status
            if wording.failure:
                failures.append({"id": entry["id"], "outcome": "template kept", "reason": wording.failure})
    return len(entries) - len(new)


def _entry(row: dict, group: str, source: str, citations: list[str], cards: list[str] | None = None,
           rules: list[str] | None = None, decklist: str | None = None, answerable: bool = True,
           protected: list[str] | None = None) -> dict:
    """`reword`, `suffix` and `protected` are working fields: what the model rewords, what is appended after it, and
    the words the rewrite must keep."""
    suffix = f"\n\n{decklist}" if decklist else ""
    return {"id": row["id"], "category": row["category"], "group": group, "template_question": row["question"] + suffix,
            "answerable": answerable, "source": source, "key_facts": row["key_facts"], "citations": citations,
            "rules": rules or row.get("rules", []), "context_rules": row.get("context_rules", []),
            "cards": cards or row.get("cards", []), "trace": row.get("trace", []),
            "reword": row["question"], "suffix": suffix, "protected": protected or []}


_FIELDS = ("id", "category", "group", "split", "question", "template_question", "wording", "answerable", "source",
           "key_facts", "citations", "rules", "context_rules", "cards", "trace")


def _ordered(entry: dict) -> dict:
    return {field: entry[field] for field in _FIELDS}


def _manifest(root: Path, folder: Path, base: Path | None, client: LlmClient, entries: list[dict], reused: int,
              failures: list[dict]) -> dict:
    by_category: dict[str, Counter] = defaultdict(Counter)
    groups: dict[str, dict[str, set]] = defaultdict(lambda: defaultdict(set))
    for entry in entries:
        by_category[entry["category"]][entry["split"]] += 1
        groups[entry["category"]][entry["split"]].add(entry["group"])
    worded_now = len(entries) - reused
    return {
        "version": folder.name,
        "base": base.name if base else None,
        "seed": split.SEED,
        "dev_share": split.DEV_SHARE,
        "model": client.model,
        "model_options": client.options,
        "prompts_sha256": prompts.fingerprint(),
        "engine_commit": _git(root, "rev-parse", "HEAD"),
        "sources_clean": _git(root, "status", "--porcelain", "--", "engine", "content", "docs/rules", "ai") == "",
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
            "policy": "A question whose template is unchanged since the base version keeps its wording. A new or "
                      "changed one is reworded, and keeps its template if the rewrite fails its check.",
            "reused": reused,
            "reworded": worded_now - len(failures),
            "template_kept": len(failures),
        },
        "failed_checks": sorted(failures, key=lambda failure: failure["id"]),
    }


def _git(root: Path, *args: str) -> str:
    try:
        return subprocess.run(["git", *args], cwd=root, capture_output=True, text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def _file_hash(file: Path) -> str:
    """Line endings are normalized, so a checkout on Windows hashes like one on Linux."""
    return hashlib.sha256(file.read_bytes().replace(b"\r\n", b"\n")).hexdigest()
