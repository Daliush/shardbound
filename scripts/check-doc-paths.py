"""
Checks that every path the agents' instructions cite exists, so that a file renamed or deleted cannot leave them
pointing at nothing. The instructions are AGENTS.md, CLAUDE.md and every Markdown file under .claude/.

A cited path is a word in backticks, or the target of a Markdown link, that holds a "/" or ends with a file extension.
It exists when it ends a tracked file or folder, read from the root or from the file that cites it: "rag/technique.py"
for ai/rag/technique.py, "rules/turn/TurnEnd.finish" for the Java class TurnEnd. "..." in a path stands for any
folders. A path the root .gitignore ignores, such as a build's output, is not checked: it exists only once something
writes it.

Usage, from anywhere in the repository, with no dependency: python3 scripts/check-doc-paths.py (python on Windows).
"""

import re
import subprocess
import sys
from fnmatch import fnmatch
from pathlib import Path, PurePosixPath

INSTRUCTIONS = ["AGENTS.md", "CLAUDE.md", ".claude/**/*.md"]

PLANNED_PATHS = {
    "docs/rulings/": "the design's rulings, created with the first one",
    "dao/game/GameDaoDatabase": "the game server's recipe for a database",
    "dao/notification/GameNotificationDaoDatabase": "the game server's recipe for a database",
}
"""Paths the instructions name before they exist, each with the reason."""

NOT_PATHS = {
    "application/x-ndjson": "a media type",
    "cross-encoder/ms-marco-MiniLM-L6-v2": "a Hugging Face model",
    "content.json": "a Java package",
}
"""Words that look like paths, each with what it is."""

FILE_EXTENSIONS = {".md", ".py", ".java", ".ts", ".html", ".scss", ".css", ".json", ".jsonl", ".yml", ".yaml", ".sh",
                   ".toml", ".xml", ".txt", ".lock", ".cmd"}

FENCED_BLOCK = re.compile(r"^\s*```.*?^\s*```", re.MULTILINE | re.DOTALL)
BACKTICKED = re.compile(r"`([^`\n]+)`")
LINK_TARGET = re.compile(r"\]\(([^)\s]+)\)")
NOT_A_PATH = re.compile(r"[<>{}*…$%@:=~|\\\"']|^[-/#]|^\.\.?$")


def main() -> int:
    root = find_repository_root()
    tracked = list_tracked_paths(root)
    ignore_patterns = read_ignore_patterns(root)
    missing = 0
    for document in list_instructions(root):
        citing_folder = PurePosixPath(document.parent.relative_to(root).as_posix())
        for path in find_cited_paths(document.read_text(encoding="utf-8")):
            if not is_found(path, citing_folder, tracked, ignore_patterns):
                print(f"{document.relative_to(root).as_posix()}: {path}")
                missing += 1
    if missing:
        print(f"{missing} cited paths do not exist: fix the path, or the text that cites it.", file=sys.stderr)
        return 1
    return 0


def find_repository_root() -> Path:
    output = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True)
    return Path(output.stdout.strip())


def list_tracked_paths(root: Path) -> list[str]:
    """Every tracked file, and every folder holding one, as "folder/"."""
    output = subprocess.run(["git", "ls-files"], cwd=root, capture_output=True, text=True, check=True)
    paths = set()
    for file in output.stdout.splitlines():
        paths.add(file)
        parts = file.split("/")
        for depth in range(1, len(parts)):
            paths.add("/".join(parts[:depth]) + "/")
    return sorted(paths)


def list_instructions(root: Path) -> list[Path]:
    documents = []
    for pattern in INSTRUCTIONS:
        for document in sorted(root.glob(pattern)):
            documents.append(document)
    return documents


def find_cited_paths(markdown: str) -> list[str]:
    """The paths a document cites outside its code blocks, each once, in order."""
    text = FENCED_BLOCK.sub("", markdown)
    candidates = []
    for span in BACKTICKED.findall(text):
        for word in span.split():
            candidates.append(word)
    for target in LINK_TARGET.findall(text):
        candidates.append(target.split("#")[0])
    paths = []
    for candidate in candidates:
        path = candidate.removeprefix("./").rstrip(".,;)")
        if looks_like_a_path(path) and path not in paths:
            paths.append(path)
    return paths


def looks_like_a_path(word: str) -> bool:
    if not word or NOT_A_PATH.search(word) or word.startswith("http"):
        return False
    return "/" in word or PurePosixPath(word).suffix in FILE_EXTENSIONS


def is_found(path: str, citing_folder: PurePosixPath, tracked: list[str], ignore_patterns: list[str]) -> bool:
    if path in PLANNED_PATHS or path in NOT_PATHS or is_ignored(path, ignore_patterns):
        return True
    return is_tracked(path, citing_folder, tracked)


def read_ignore_patterns(root: Path) -> list[str]:
    """The patterns of the root .gitignore. A machine's own excludes are left out, as CI does not have them."""
    patterns = []
    for line in (root / ".gitignore").read_text(encoding="utf-8").splitlines():
        pattern = line.strip()
        if pattern and not pattern.startswith(("#", "!")):
            patterns.append(pattern)
    return patterns


def is_ignored(path: str, ignore_patterns: list[str]) -> bool:
    """
    Whether .gitignore ignores the path or one of its folders. A pattern with a "/" inside starts at the root
    ("ai/.corpus/"); any other pattern matches a name at any depth ("target/", "*.pyc").
    """
    names = path.rstrip("/").split("/")
    for pattern in ignore_patterns:
        bare_pattern = pattern.strip("/")
        if "/" in bare_pattern:
            if path.rstrip("/") == bare_pattern or path.startswith(bare_pattern + "/"):
                return True
        else:
            for name in names:
                if fnmatch(name, bare_pattern):
                    return True
    return False


def is_tracked(path: str, citing_folder: PurePosixPath, tracked: list[str]) -> bool:
    """Whether a tracked path ends with this one, read from the root or from the citing folder."""
    patterns = [match_path_ending(path)]
    if path.startswith("../"):
        patterns.append(match_path_ending(normalize_path(citing_folder / path)))
    for tracked_path in tracked:
        for pattern in patterns:
            if pattern.search(tracked_path):
                return True
    return False


def match_path_ending(path: str) -> re.Pattern[str]:
    """
    A path ending with this one, whole names only. A last name with no file extension may be a class, or a class and
    one of its members: "rules/turn/TurnEnd.finish" ends rules/turn/TurnEnd.java.
    """
    folders, _, last = path.rstrip("/").rpartition("/")
    start = re.escape(folders + "/").replace(re.escape("..."), ".*") if folders else ""
    if PurePosixPath(last).suffix in FILE_EXTENSIONS:
        end = re.escape(last) + "$"
    else:
        end = re.escape(last.split(".")[0]) + r"(\.[a-z]+|/)?$"
    return re.compile("(^|/)" + start + end)


def normalize_path(path: PurePosixPath) -> str:
    """The path with its ".." folded: docs/../content/cards becomes content/cards."""
    parts = []
    for part in path.parts:
        if part == ".." and parts:
            parts.pop()
        elif part != ".":
            parts.append(part)
    return "/".join(parts)


if __name__ == "__main__":
    sys.exit(main())
