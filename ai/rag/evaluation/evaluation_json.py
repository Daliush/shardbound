"""
An evaluation as JSON, the format the Java backend serves to the benchmark explorer (design doc §8.2). Every
storage writes this same document:

{"dataset": {"version": "v1", "split": "dev"}, "evaluated_at": "2026-10-15T10:42:07Z", "code": "45c56b9",
 "description": "Step 3: BM25 and its variants",
 "runs": [{"layers": {"retriever": "bm25", "answer": null},
           "description": "BM25 (lucene formula, k1 = 1.5, b = 0.75, stemmed words, stop words kept)",
           "scores": {"overall": {…}, "by_category": {…}},
           "questions": [{"question_id": …, "top_chunk_ids": […], …}]}]}
"""

import json
from dataclasses import asdict

from rag.evaluation.runs import Evaluation


def convert_evaluation_to_json(evaluation: Evaluation) -> str:
    runs = []
    for run in evaluation.runs:
        runs.append({"layers": asdict(run.layers),
                     "description": run.description,
                     "scores": {"overall": asdict(run.report.overall),
                                "by_category": {category: asdict(scores)
                                                for category, scores in run.report.by_category.items()}},
                     "questions": [asdict(result) for result in run.report.questions]})
    document = {"dataset": {"version": evaluation.version, "split": evaluation.split},
                "evaluated_at": evaluation.evaluated_at.strftime("%Y-%m-%dT%H:%M:%SZ"),
                "code": evaluation.code_commit,
                "description": evaluation.description,
                "runs": runs}
    return json.dumps(document, indent=1, ensure_ascii=False) + "\n"


def name_evaluation(evaluation: Evaluation) -> str:
    """`v1/dev/2026-10-15T10-42-07Z.json`: where any storage keeps it. The names of a split sort by time."""
    return f"{evaluation.version}/{evaluation.split}/{evaluation.evaluated_at.strftime('%Y-%m-%dT%H-%M-%SZ')}.json"
