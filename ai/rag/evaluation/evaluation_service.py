"""The evaluation's flow: build what is evaluated, run it on every question, score the results, save the runs."""

from pathlib import Path

from rag.chunks import read_chunks
from rag.evaluation.questions import Question, read_questions
from rag.evaluation.run_file import find_runs_file, read_code_commit, save_runs
from rag.evaluation.runs import Layers, Run
from rag.evaluation.scores import Report, average_scores, score_question
from rag.retrievers import Retriever
from rag.retrievers.registry import build_retriever


def evaluate_all(retriever_names: list[str], root: Path, version: str, split: str) -> list[Run]:
    """
    One run per retriever, its answer layer empty until there is a generator, on the questions of
    `dataset-<split>.jsonl`; saved in `evals/reports/<version>/<split>.json` for the frontend.
    """
    chunks = read_chunks(root)
    questions = read_questions(root, version, split)
    runs = []
    for retriever_name in retriever_names:
        retriever = build_retriever(retriever_name, chunks)
        runs.append(Run(Layers(retriever=retriever_name, answer=None), evaluate(retriever, questions)))
    save_runs(find_runs_file(root, version, split), version, split, runs, read_code_commit(root))
    return runs


def evaluate(retriever: Retriever, questions: list[Question]) -> Report:
    """
    Today, the retrieval part: ranks the chunks of every answerable question once (an unanswerable one has no
    relevant chunk to find), scores each, then averages over all of them and by category.
    """
    results = []
    for question in questions:
        if question.answerable:
            results.append(score_question(question, retriever.rank_chunks(question.text)))
    by_category = {}
    for category in sorted({result.category for result in results}):
        by_category[category] = average_scores([result for result in results if result.category == category])
    return Report(overall=average_scores(results), by_category=by_category, questions=results)
