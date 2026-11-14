"""The answers' evaluation: the Arbiter answers every question of a split, then code scores each answer."""

from dataclasses import replace
from datetime import datetime, timezone
from pathlib import Path

from arbiter.arbiter import Arbiter, read_indexed_chunks
from arbiter.evaluation.answer_evaluation_repository import AnswerEvaluationRepository
from arbiter.evaluation.answer_keys import AnswerKey, read_answer_keys
from arbiter.evaluation.answer_scores import list_metric_descriptions, report_answers, score_answer
from arbiter.evaluation.runs import AnswerEvaluation, AnswerRun
from arbiter.generation import Generator
from arbiter.generation.completion_cache import CACHE_FOLDER, CompletionCache
from arbiter.generation.registry import build_generator
from rag.baseline import BASELINE
from rag.contexts.registry import build_context
from rag.device import describe_device
from rag.evaluation.code_commit import read_code_commit
from rag.evaluation.runs import Layers
from rag.named_cards import NamedCardFinder
from rag.pipeline import Pipeline, build_ranking_techniques


def evaluate_answers(generator_names: list[str], context_names: list[str], root: Path, version: str, split: str,
                     description: str, repository: AnswerEvaluationRepository) -> AnswerEvaluation:
    """
    Each generator, answering from each context of the baseline's ranking, on every question of
    `dataset-<split>.jsonl`, unanswerable ones included: one run per context and generator, saved by `repository` as
    a new document, with the `description` of why it was run. The indexed chunks are read once, and the techniques
    that rank them are built once: every context cuts the same ranking, and the cards each question names are found
    in the same chunks.
    """
    evaluated_at = datetime.now(timezone.utc)
    code_commit = read_code_commit(root)
    chunks = read_indexed_chunks()
    answer_keys = read_answer_keys(root, version, split, NamedCardFinder(chunks))
    ranking_techniques = build_ranking_techniques(BASELINE, chunks)
    runs = []
    for context_name in context_names:
        pipeline_layers = replace(BASELINE, context=context_name)
        pipeline = Pipeline(ranking_techniques, build_context(context_name, chunks), chunks)
        for generator_name in generator_names:
            generator = build_generator(generator_name, CompletionCache(root / CACHE_FOLDER))
            runs.append(evaluate_run(pipeline_layers, pipeline, generator, answer_keys))
    evaluation = AnswerEvaluation(version=version, split=split, evaluated_at=evaluated_at, code_commit=code_commit,
                                  device=describe_device(), description=description,
                                  metrics=list_metric_descriptions(), runs=runs)
    repository.save_evaluation(evaluation)
    return evaluation


def evaluate_run(pipeline_layers: Layers, pipeline: Pipeline, generator: Generator,
                 answer_keys: list[AnswerKey]) -> AnswerRun:
    """The Arbiter answers each question, then each answer is scored against its key."""
    arbiter = Arbiter(pipeline, generator)
    results = []
    for answer_key in answer_keys:
        reply = arbiter.answer_question(answer_key.question)
        results.append(score_answer(answer_key, reply, generator.price))
    layers = replace(pipeline_layers, answer=generator.name)
    return AnswerRun(layers, describe_run(pipeline_layers, generator), generator.settings, generator.price,
                     report_answers(results))


def describe_run(pipeline_layers: Layers, generator: Generator) -> str:
    """The pipeline by the names of its techniques, then the generator as it describes itself."""
    techniques = [pipeline_layers.retriever]
    for technique in (pipeline_layers.expand, pipeline_layers.rerank, pipeline_layers.context):
        if technique is not None:
            techniques.append(technique)
    return f"The context of {' + '.join(techniques)}; then {generator.description}"
