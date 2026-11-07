"""The Arbiter's packages depend on each other one way: the evaluation reads the rest, the rest never reads it."""

import ast
from pathlib import Path

AI = Path(__file__).resolve().parents[2]


def list_imported_modules(file: Path) -> list[str]:
    modules = []
    for node in ast.walk(ast.parse(file.read_text(encoding="utf-8"))):
        if isinstance(node, ast.Import):
            for alias in node.names:
                modules.append(alias.name)
        elif isinstance(node, ast.ImportFrom) and node.module is not None:
            modules.append(node.module)
    return modules


def find_imports_of(package: str, folder: Path) -> list[str]:
    """Each `file: module` of `folder` that imports `package` or a module of it."""
    found = []
    for file in sorted(folder.rglob("*.py")):
        for module in list_imported_modules(file):
            if module == package or module.startswith(package + "."):
                found.append(f"{file.relative_to(AI)}: {module}")
    return found


def test_generation_reads_neither_the_rag_nor_the_rest_of_the_arbiter():
    generation = AI / "arbiter" / "generation"

    assert find_imports_of("rag", generation) == []
    assert find_imports_of("arbiter.arbiter", generation) == []
    assert find_imports_of("arbiter.evaluation", generation) == []


def test_the_rag_reads_nothing_of_the_arbiter():
    assert find_imports_of("arbiter", AI / "rag") == []


def test_only_the_evaluation_reads_the_evaluation():
    found = []
    for file in sorted((AI / "arbiter").rglob("*.py")):
        if "evaluation" not in file.relative_to(AI / "arbiter").parts:
            for module in list_imported_modules(file):
                if module.startswith("arbiter.evaluation"):
                    found.append(f"{file.relative_to(AI)}: {module}")

    assert found == []
