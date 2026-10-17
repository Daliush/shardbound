import json
from datetime import datetime, timezone

from rag.evaluation.evaluation_json import convert_evaluation_to_json, name_evaluation
from rag.evaluation.runs import Evaluation, Layers, Run
from rag.evaluation.scores import QuestionResult, Report, Scores

EVALUATED_AT = datetime(2026, 10, 15, 10, 42, 7, tzinfo=timezone.utc)


def build_evaluation(retrievers: list[str]) -> Evaluation:
    scores = Scores(question_count=1, mean_recall_at={10: 1.0, 20: 1.0}, mean_reciprocal_rank=1.0,
                    mean_candidate_count=2.0, mean_recall_curve=[1.0, 1.0])
    result = QuestionResult(question_id="rule/2.2", category="simple-rule", top_chunk_ids=["2.2", "2.1"],
                            relevant_ranks={"2.2": 1, "4.1": None}, recall_at={10: 0.5, 20: 0.5},
                            reciprocal_rank=1.0, candidate_count=2)
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


def test_writes_each_question_relevant_ranks_and_each_score_recall_curve():
    run = json.loads(convert_evaluation_to_json(build_evaluation(["bm25"])))["runs"][0]

    assert run["questions"][0]["relevant_ranks"] == {"2.2": 1, "4.1": None}
    assert run["scores"]["overall"]["mean_recall_curve"] == [1.0, 1.0]


def test_writes_exactly_the_fields_of_a_score_and_of_a_question():
    run = json.loads(convert_evaluation_to_json(build_evaluation(["bm25"])))["runs"][0]

    assert set(run["scores"]["overall"]) == {"question_count", "mean_recall_at", "mean_reciprocal_rank",
                                            "mean_candidate_count", "mean_recall_curve"}
    assert set(run["questions"][0]) == {"question_id", "category", "top_chunk_ids", "relevant_ranks", "recall_at",
                                       "reciprocal_rank", "candidate_count"}


def test_names_an_evaluation_by_its_dataset_split_and_time():
    assert name_evaluation(build_evaluation(["bm25"])) == "v1/dev/2026-10-15T10-42-07Z.json"
