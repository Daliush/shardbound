"""The evaluation's flow: build the tree of every pipeline the techniques make, score each node, save the runs."""

import time
from datetime import datetime, timezone
from pathlib import Path

from rag.chunks import Chunk
from rag.evaluation.code_commit import read_code_commit
from rag.evaluation.questions import Question, read_questions
from rag.evaluation.runs import Evaluation, Run
from rag.evaluation.scores import QuestionResult, Report, score_group, score_question
from rag.evaluation.stages import Stage, StepResult
from rag.expanders.registry import build_expander
from rag.repository.chunk_repository import ChunkRepositoryPostgres
from rag.repository.database import connect_database
from rag.repository.evaluation_repository import EvaluationRepository
from rag.rerankers.registry import build_reranker
from rag.retrievers.registry import build_retriever
from rag.technique import Step, Technique


def evaluate_all(retriever_names: list[str], expander_names: list[str], reranker_names: list[str], root: Path,
                 version: str, split: str, description: str, evaluation_repository: EvaluationRepository) -> Evaluation:
    """
    Every pipeline the techniques named make, on the questions of `dataset-<split>.jsonl`; saved by
    `evaluation_repository` as a new document, with the `description` of why it was run. Every technique reads the
    indexed chunks from the database, never the engine's file: a run can't mix two versions of the corpus.
    """
    evaluated_at = datetime.now(timezone.utc)
    code_commit = read_code_commit(root)
    chunks = ChunkRepositoryPostgres(connect_database()).find_all_chunks()
    if not chunks:
        raise LookupError("No chunks in the database: build the index with `uv run python -m rag index`")
    questions = read_questions(root, version, split)
    techniques_after_retrieval: dict[Step, list[Technique]] = {Step.EXPAND: [], Step.RERANK: []}
    for expander_name in expander_names:
        techniques_after_retrieval[Step.EXPAND].append(build_expander(expander_name, chunks))
    for reranker_name in reranker_names:
        techniques_after_retrieval[Step.RERANK].append(build_reranker(reranker_name, chunks))
    evaluation = Evaluation(version=version, split=split, evaluated_at=evaluated_at, code_commit=code_commit,
                            description=description,
                            runs=evaluate_combinations(retriever_names, chunks, techniques_after_retrieval, questions))
    evaluation_repository.save_evaluation(evaluation)
    return evaluation


def evaluate_combinations(retriever_names: list[str], chunks: list[Chunk],
                          techniques_after_retrieval: dict[Step, list[Technique]],
                          questions: list[Question]) -> list[Run]:
    """
    One run per node of the tree: each retriever, then every pipeline that continues it. Only the answerable
    questions are asked: an unanswerable one has no relevant chunk to find. The retrievers are built one at a time, as
    a dense one holds its model.
    """
    answerable = []
    for question in questions:
        if question.answerable:
            answerable.append(question)
    runs = []
    for retriever_name in retriever_names:
        retrieved = apply(build_retriever(retriever_name, chunks), None, answerable)
        runs.append(score(retrieved))
        runs += continue_with(retrieved, [Step.EXPAND, Step.RERANK], techniques_after_retrieval, answerable)
    return runs


def continue_with(parent: Stage, steps: list[Step], techniques: dict[Step, list[Technique]],
                  questions: list[Question]) -> list[Run]:
    """
    The runs of every node under `parent`: each technique of the next step, and the step skipped, then the same for
    the steps after it. Each node is computed once; every node under it starts from what it hands on.
    """
    if not steps:
        return []
    step, later_steps = steps[0], steps[1:]
    runs = continue_with(parent, later_steps, techniques, questions)
    for technique in techniques[step]:
        child = apply(technique, parent, questions)
        runs.append(score(child))
        runs += continue_with(child, later_steps, techniques, questions)
    return runs


def apply(technique: Technique, parent: Stage | None, questions: list[Question]) -> Stage:
    """The technique on every question, given what the parent hands on for it: nothing to a retriever."""
    results = {}
    for question in questions:
        given = parent.hand_on(question) if parent is not None else []
        start = time.perf_counter()
        chunk_ids = technique.process(question.text, given)
        results[question.id] = StepResult(question, chunk_ids, time.perf_counter() - start)
    return Stage(technique, parent, results)


def score(stage: Stage) -> Run:
    """The node's run: its metrics on every question, then the scores of all of them and of each category."""
    results = []
    results_by_category: dict[str, list[QuestionResult]] = {}
    for step_result in stage.results.values():
        result = score_question(step_result.question, step_result.chunk_ids, stage.sum_seconds(step_result.question))
        results.append(result)
        results_by_category.setdefault(result.category, []).append(result)
    by_category = {}
    for category in sorted(results_by_category):
        by_category[category] = score_group(results_by_category[category])
    report = Report(overall=score_group(results), by_category=by_category, questions=results)
    return Run(stage.name_layers(), stage.describe(), report)
