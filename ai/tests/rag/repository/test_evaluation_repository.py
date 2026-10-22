import json
from dataclasses import replace
from datetime import datetime, timezone
from pathlib import Path

from rag.evaluation.runs import Evaluation
from rag.repository.evaluation_repository import EvaluationRepositoryFile

EVALUATION = Evaluation(version="v1", split="dev", code_commit="45c56b9", description="A check", metrics=[],
                        runs=[],
                        evaluated_at=datetime(2026, 10, 15, 9, 0, 0, tzinfo=timezone.utc))


def test_saves_each_evaluation_as_a_new_file_under_evals_reports(tmp_path: Path):
    repository = EvaluationRepositoryFile(tmp_path)
    evening = replace(EVALUATION, evaluated_at=datetime(2026, 10, 15, 18, 0, 0, tzinfo=timezone.utc))

    morning_file = Path(repository.save_evaluation(EVALUATION))
    repository.save_evaluation(evening)

    assert morning_file == tmp_path / "evals" / "reports" / "v1" / "dev" / "2026-10-15T09-00-00Z.json"
    assert sorted(path.name for path in morning_file.parent.iterdir()) == ["2026-10-15T09-00-00Z.json",
                                                                          "2026-10-15T18-00-00Z.json"]
    assert json.loads(morning_file.read_text(encoding="utf-8"))["code"] == "45c56b9"
