"""The evaluation's flow: build what is evaluated, run it on every question, score the results, save them."""

from datetime import datetime, timezone
from pathlib import Path

from rag.evaluation.code_commit import read_code_commit
from rag.evaluation.questions import Question, read_questions
from rag.evaluation.runs import Evaluation, Layers, Run
from rag.evaluation.scores import Report, average_scores, score_question
from rag.repository.chunk_repository import ChunkRepositoryPostgres
from rag.repository.database import connect_database
from rag.repository.evaluation_repository import EvaluationRepository
from rag.retrievers import Retriever
from rag.retrievers.registry import build_retriever


def evaluate_all(retriever_names: list[str], root: Path, version: str, split: str,
                 evaluation_repository: EvaluationRepository) -> Evaluation:
    """
    One run per retriever, its answer layer empty until there is a generator, on the questions of
    `dataset-<split>.jsonl`; saved by `evaluation_repository` as a new document. Every retriever reads the indexed
    chunks from the database, never the engine's file: a run can't mix two versions of the corpus.
    """
    evaluated_at = datetime.now(timezone.utc)
    code_commit = read_code_commit(root)
    chunks = ChunkRepositoryPostgres(connect_database()).find_all_chunks()
    if not chunks:
        raise LookupError("No chunks in the database: build the index with `uv run python -m rag index`")
    questions = read_questions(root, version, split)
    runs = []
    for retriever_name in retriever_names:
        retriever = build_retriever(retriever_name, chunks)
        runs.append(Run(Layers(retriever=retriever_name, answer=None), evaluate(retriever, questions)))
    evaluation = Evaluation(version=version, split=split, evaluated_at=evaluated_at, code_commit=code_commit,
                            runs=runs)
    evaluation_repository.save_evaluation(evaluation)
    return evaluation


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
