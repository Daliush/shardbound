import json
from datetime import datetime, timezone

from rag.evaluation.evaluation_json import convert_evaluation_to_json, name_evaluation
from rag.evaluation.runs import Evaluation, Layers, Run
from rag.evaluation.scores import QuestionResult, Report, Scores

EVALUATED_AT = datetime(2026, 10, 15, 10, 42, 7, tzinfo=timezone.utc)


def build_evaluation(retrievers: list[str]) -> Evaluation:
    scores = Scores(question_count=1, mean_recall_at={10: 1.0, 20: 1.0}, mean_reciprocal_rank=1.0,
                    mean_candidate_recall=1.0, mean_candidate_count=2.0)
    result = QuestionResult(question_id="rule/2.2", category="simple-rule", top_chunk_ids=["2.2", "2.1"],
                            recall_at={10: 1.0, 20: 1.0}, reciprocal_rank=1.0, candidate_recall=1.0, candidate_count=2)
    runs = []
    for retriever in retrievers:
        runs.append(Run(Layers(retriever=retriever), f"what {retriever} does",
                        Report(overall=scores, by_category={"simple-rule": scores}, questions=[result])))
    return Evaluation(version="v1", split="dev", evaluated_at=EVALUATED_AT, code_commit="45c56b9",
                      description="Step 3: BM25", runs=runs)


def test_writes_the_dataset_the_time_the_code_and_why_once_then_each_run_by_its_layers_and_description():
    document = json.loads(convert_evaluation_to_json(build_evaluation(["random", "bm25"])))

    assert document["dataset"] == {"version": "v1", "split": "dev"}
    assert document["evaluated_at"] == "2026-10-15T10:42:07Z"
    assert document["code"] == "45c56b9"
    assert document["description"] == "Step 3: BM25"
    assert [run["layers"] for run in document["runs"]] == [{"retriever": "random", "answer": None},
                                                          {"retriever": "bm25", "answer": None}]
    assert [run["description"] for run in document["runs"]] == ["what random does", "what bm25 does"]
    assert document["runs"][1]["scores"]["overall"]["mean_recall_at"] == {"10": 1.0, "20": 1.0}
    assert document["runs"][1]["questions"][0]["top_chunk_ids"] == ["2.2", "2.1"]


def test_names_an_evaluation_by_its_dataset_split_and_time():
    assert name_evaluation(build_evaluation(["bm25"])) == "v1/dev/2026-10-15T10-42-07Z.json"
