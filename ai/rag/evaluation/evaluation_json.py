"""
An evaluation as JSON, the format the Java backend serves to the benchmark explorer (design doc §8.2). Every
storage writes this same document:

{"dataset": {"version": "v1", "split": "dev"}, "evaluated_at": "2026-10-15T10:42:07Z", "code": "45c56b9",
 "device": "cpu", "description": "Step 3: BM25 and its variants",
 "metrics": [{"path": "mean_recall_at.10", "name": "Recall@10", "display": "share", "what": …, "how": …,
              "represents": …}, …],
 "runs": [{"layers": {"retriever": "bm25", "answer": null},
           "description": "BM25 (lucene formula, k1 = 1.5, b = 0.75, stemmed words, stop words kept)",
           "scores": {"overall": {…}, "by_category": {…}},
           "questions": [{"question_id": …, "top_chunk_ids": […], …}]}]}
"""

import json
from dataclasses import asdict

from rag.evaluation.metric_description import MetricDescription
from rag.evaluation.runs import Evaluation
from rag.evaluation.scores import QuestionResult, Scores


def convert_evaluation_to_json(evaluation: Evaluation) -> str:
    runs = []
    for run in evaluation.runs:
        by_category = {}
        for category, scores in run.report.by_category.items():
            by_category[category] = convert_scores(scores)
        questions = []
        for result in run.report.questions:
            questions.append(convert_question_result(result))
        runs.append({"layers": asdict(run.layers),
                     "description": run.description,
                     "scores": {"overall": convert_scores(run.report.overall), "by_category": by_category},
                     "questions": questions})
    document = {"dataset": {"version": evaluation.version, "split": evaluation.split},
                "evaluated_at": evaluation.evaluated_at.strftime("%Y-%m-%dT%H:%M:%SZ"),
                "code": evaluation.code_commit,
                "device": evaluation.device,
                "description": evaluation.description,
                "metrics": convert_metric_descriptions(evaluation.metrics),
                "runs": runs}
    return json.dumps(document, indent=1, ensure_ascii=False) + "\n"


def convert_metric_descriptions(descriptions: list[MetricDescription]) -> list[dict]:
    """Each value's description, its display by name: {"path": "mean_recall_at.10", "display": "share", …}."""
    documents = []
    for description in descriptions:
        documents.append({"path": description.path,
                          "name": description.name,
                          "display": description.display.value,
                          "what": description.what,
                          "how": description.how,
                          "represents": description.represents})
    return documents


def convert_scores(scores: Scores) -> dict:
    """The scores of a group, each metric's mean under `mean_` and its key: {"mean_recall_at": {"10": …}, …}."""
    document = {"question_count": scores.question_count}
    document.update(nest_by_key(scores.metric_means, "mean_"))
    document["mean_candidate_count"] = scores.mean_candidate_count
    document["mean_recall_curve"] = scores.mean_recall_curve
    document["latency_p50_seconds"] = scores.latency_p50_seconds
    document["latency_p95_seconds"] = scores.latency_p95_seconds
    return document


def convert_question_result(result: QuestionResult) -> dict:
    """A question's result, each metric's value under its key: {"recall_at": {"10": …}, "reciprocal_rank": …}."""
    document = {"question_id": result.question_id,
                "category": result.category,
                "top_chunk_ids": result.top_chunk_ids,
                "relevant_ranks": result.relevant_ranks}
    document.update(nest_by_key(result.metric_values, ""))
    document["candidate_count"] = result.candidate_count
    document["seconds"] = result.seconds
    return document


def nest_by_key(values: dict[str, float], prefix: str) -> dict:
    """Each value under its metric's key, a dot nesting it: `recall_at.10` gives {"recall_at": {"10": …}}. Every
    evaluation file has this shape, which the lab, `rag compare` and the CI gate read."""
    nested: dict = {}
    for key, value in values.items():
        name, _, cutoff = key.partition(".")
        if cutoff:
            nested.setdefault(prefix + name, {})[cutoff] = value
        else:
            nested[prefix + name] = value
    return nested


def name_evaluation(evaluation: Evaluation) -> str:
    """`v1/dev/2026-10-15T10-42-07Z.json`: where any storage keeps it. The names of a split sort by time."""
    return f"{evaluation.version}/{evaluation.split}/{evaluation.evaluated_at.strftime('%Y-%m-%dT%H-%M-%SZ')}.json"
