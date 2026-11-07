"""
An evaluation of the answers as JSON. It has the shape of a retrieval evaluation, so that the benchmark explorer
(design doc §8.2) reads both the same way: the metrics described, then each run with its layers, its scores and its
questions.

{"dataset": {"version": "v2", "split": "dev"}, "evaluated_at": "2026-11-08T10:42:07Z", "code": "45c56b9",
 "device": "cpu, torch 2.14.1+cpu", "description": "Step 9: the first checks of the answers",
 "metrics": [{"path": "mean_citation_recall", "name": "Citation recall", "display": "share", "what": …}, …],
 "runs": [{"layers": {"retriever": "rrf-bm25-dense", "expand": "refs", "rerank": "minilm-l6",
                      "answer": "claude-haiku-5.5"},
           "description": "The context of rrf-bm25-dense + refs + minilm-l6; then Claude (…)",
           "generator": {"settings": {"model": "claude-haiku-5-5", …},
                         "price": {"input_per_million": 0.1, "output_per_million": 0.5}},
           "scores": {"overall": {"question_count": 72, "mean_citation_recall": 0.41, "scored_citation_recall": 61,
                                  …},
                      "by_category": {…}},
           "questions": [{"question_id": …, "context_ids": […], "answer": {"answer": …, "citations": […],
                          "answerable": true}, "citation_recall": 0.29, …}]}]}
"""

import json
from dataclasses import asdict

from arbiter.evaluation.answer_scores import AnswerResult, AnswerScores
from arbiter.evaluation.runs import AnswerEvaluation
from rag.evaluation.evaluation_json import convert_metric_descriptions


def convert_evaluation_to_json(evaluation: AnswerEvaluation) -> str:
    runs = []
    for run in evaluation.runs:
        by_category = {}
        for category, scores in run.report.by_category.items():
            by_category[category] = convert_scores(scores)
        questions = []
        for result in run.report.questions:
            questions.append(convert_answer_result(result))
        runs.append({"layers": asdict(run.layers),
                     "description": run.description,
                     "generator": {"settings": run.generator_settings, "price": asdict(run.price)},
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


def convert_scores(scores: AnswerScores) -> dict:
    """The scores of a group: each metric's mean under `mean_` and its key, how many questions it scored under
    `scored_`, then the group's own values."""
    document = {"question_count": scores.question_count}
    for key, mean in scores.metric_means.items():
        document[f"mean_{key}"] = mean
    for key, count in scores.metric_counts.items():
        document[f"scored_{key}"] = count
    document["mean_input_tokens"] = scores.mean_input_tokens
    document["mean_output_tokens"] = scores.mean_output_tokens
    document["mean_cost_dollars"] = scores.mean_cost_dollars
    document["latency_p50_seconds"] = scores.latency_p50_seconds
    document["latency_p95_seconds"] = scores.latency_p95_seconds
    return document


def convert_answer_result(result: AnswerResult) -> dict:
    """A question's result: what the Arbiter read and answered, then each metric's value under its key, null when it
    does not apply, then what the answer cost."""
    document = {"question_id": result.question_id,
                "category": result.category,
                "context_ids": result.context_ids,
                "answer": result.answer.model_dump() if result.answer is not None else None,
                "format_failures": result.format_failures}
    document.update(result.metric_values)
    document["input_tokens"] = result.usage.input_tokens
    document["output_tokens"] = result.usage.output_tokens
    document["cost_dollars"] = result.cost_dollars
    document["seconds"] = result.seconds
    return document


def name_evaluation(evaluation: AnswerEvaluation) -> str:
    """`v2/dev/2026-11-08T10-42-07Z.json`: where any storage keeps it. The names of a split sort by time."""
    return f"{evaluation.version}/{evaluation.split}/{evaluation.evaluated_at.strftime('%Y-%m-%dT%H-%M-%SZ')}.json"
