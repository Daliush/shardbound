import json
from datetime import datetime, timezone

from arbiter.evaluation.answer_evaluation_repository import AnswerEvaluationRepositoryFile
from arbiter.evaluation.answer_scores import (AnswerReport, AnswerResult, AnswerScores, list_metric_descriptions,
                                              report_answers)
from arbiter.evaluation.evaluation_json import convert_evaluation_to_json, convert_scores, name_evaluation
from arbiter.evaluation.runs import AnswerEvaluation, AnswerRun
from arbiter.generation.completion import Price, Usage
from rag.evaluation.runs import Layers
from tests.arbiter.evaluation.answer_key_examples import answer_citing

EVALUATED_AT = datetime(2026, 11, 8, 10, 42, 7, tzinfo=timezone.utc)
LAYERS = Layers(retriever="rrf-bm25-dense", expand="refs", rerank="minilm-l6", context="top10-plus-named-cards",
                answer="claude-haiku-5.5")


def build_result(answered: bool) -> AnswerResult:
    return AnswerResult(question_id="rule/2.2", category="simple-rule", context_ids=["2.2", "2.1"],
                        answer=answer_citing("2.2") if answered else None,
                        format_failures=[] if answered else ["stopped on refusal: …", "stopped on refusal: …"],
                        metric_values={"valid_answer": 1.0 if answered else 0.0,
                                       "answered": 1.0 if answered else 0.0, "correct_refusal": None,
                                       "citation_recall": 1.0 if answered else 0.0,
                                       "citation_precision": 1.0 if answered else None, "step_order": None},
                        usage=Usage(1600, 600), cost_dollars=0.00046, seconds=4.2)


def build_evaluation() -> AnswerEvaluation:
    report = report_answers([build_result(True), build_result(False)])
    run = AnswerRun(LAYERS, "what answers", {"model": "claude-haiku-5-5", "effort": "medium"}, Price(0.10, 0.50),
                    report)
    return AnswerEvaluation(version="v2", split="dev", evaluated_at=EVALUATED_AT, code_commit="45c56b9",
                            device="cpu, torch 2.14.1+cpu", description="Step 9: the first checks of the answers",
                            metrics=list_metric_descriptions(), runs=[run])


def test_writes_the_shape_of_a_retrieval_evaluation_with_the_layers_of_the_answer():
    document = json.loads(convert_evaluation_to_json(build_evaluation()))

    assert list(document) == ["dataset", "evaluated_at", "code", "device", "description", "metrics", "runs"]
    assert document["dataset"] == {"version": "v2", "split": "dev"}
    assert document["evaluated_at"] == "2026-11-08T10:42:07Z"
    assert document["device"] == "cpu, torch 2.14.1+cpu"
    run = document["runs"][0]
    assert run["layers"] == {"retriever": "rrf-bm25-dense", "expand": "refs", "rerank": "minilm-l6",
                             "context": "top10-plus-named-cards", "answer": "claude-haiku-5.5"}
    assert run["generator"] == {"settings": {"model": "claude-haiku-5-5", "effort": "medium"},
                                "price": {"input_per_million": 0.10, "output_per_million": 0.50}}
    assert list(run["scores"]) == ["overall", "by_category"]
    assert run["scores"]["overall"]["mean_citation_precision"] == 1.0
    assert run["scores"]["overall"]["scored_citation_precision"] == 1
    assert run["scores"]["overall"]["mean_step_order"] is None


def test_keeps_what_each_question_read_and_answered_null_without_an_answer():
    questions = json.loads(convert_evaluation_to_json(build_evaluation()))["runs"][0]["questions"]

    assert questions[0] == {"question_id": "rule/2.2", "category": "simple-rule", "context_ids": ["2.2", "2.1"],
                            "answer": {"answer": "…", "citations": ["2.2"], "answerable": True},
                            "format_failures": [], "valid_answer": 1.0, "answered": 1.0, "correct_refusal": None,
                            "citation_recall": 1.0, "citation_precision": 1.0, "step_order": None, "input_tokens": 1600,
                            "output_tokens": 600, "cost_dollars": 0.00046, "seconds": 4.2}
    assert questions[1]["answer"] is None
    assert questions[1]["format_failures"] == ["stopped on refusal: …", "stopped on refusal: …"]


def test_describes_every_value_of_a_group_s_scores_for_the_benchmark_explorer():
    report: AnswerReport = build_evaluation().runs[0].report
    scores: AnswerScores = report.overall
    described = set()
    for description in list_metric_descriptions():
        described.add(description.path)

    assert set(convert_scores(scores)) == described


def test_names_an_evaluation_by_its_dataset_split_and_time():
    assert name_evaluation(build_evaluation()) == "v2/dev/2026-11-08T10-42-07Z.json"


def test_saves_each_evaluation_as_a_new_file_beside_the_retrieval_s_reports(tmp_path):
    saved = AnswerEvaluationRepositoryFile(tmp_path).save_evaluation(build_evaluation())

    file = tmp_path / "evals" / "answers" / "v2" / "dev" / "2026-11-08T10-42-07Z.json"
    assert saved == str(file)
    assert json.loads(file.read_text(encoding="utf-8"))["code"] == "45c56b9"
