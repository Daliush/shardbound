"""The Arbiter: the RAG's context for a question, then the answer a generator writes from it."""

from dataclasses import dataclass
from pathlib import Path

from arbiter.generation import Generation, Generator
from arbiter.generation.completion_cache import CACHE_FOLDER, CompletionCache
from arbiter.generation.prompt import ContextChunk, write_prompt
from arbiter.generation.registry import build_generator
from rag.evaluation.runs import Layers
from rag.pipeline import Pipeline, build_pipeline
from rag.repository.chunk_repository import ChunkRepositoryPostgres
from rag.repository.database import connect_database


@dataclass(frozen=True)
class Reply:
    """What the Arbiter read and what it answered."""

    question: str
    context: list[ContextChunk]
    generation: Generation


class Arbiter:
    """Answers a rules question: its pipeline gives the context, its generator writes the answer. A configuration
    without RAG has no pipeline, and answers from no context."""

    def __init__(self, pipeline: Pipeline | None, generator: Generator) -> None:
        self._pipeline = pipeline
        self._generator = generator

    def answer_question(self, question: str) -> Reply:
        context = self._retrieve_context(question)
        return Reply(question, context, self._generator.generate_answer(write_prompt(question, context)))

    def _retrieve_context(self, question: str) -> list[ContextChunk]:
        context = []
        if self._pipeline is None:
            return context
        for chunk in self._pipeline.retrieve_context(question):
            context.append(ContextChunk(chunk.id, chunk.kind, chunk.text))
        return context


def build_arbiter(layers: Layers, generator_name: str, root: Path) -> Arbiter:
    """The Arbiter of the pipeline `layers` names, on the indexed chunks, and of the generator named, whose completions
    are kept in the repository at `root`."""
    chunks = ChunkRepositoryPostgres(connect_database()).find_all_chunks()
    if not chunks:
        raise LookupError("No chunks in the database: build the index with `uv run python -m rag index`")
    return Arbiter(build_pipeline(layers, chunks), build_generator(generator_name, CompletionCache(root / CACHE_FOLDER)))
