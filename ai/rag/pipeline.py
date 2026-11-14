"""The pipeline the Arbiter calls: the techniques a run's layers name, one after the other, the context last."""

from rag.chunks import Chunk
from rag.contexts import ContextBuilder
from rag.contexts.registry import build_context
from rag.evaluation.runs import Layers
from rag.expanders.registry import build_expander
from rag.rerankers.registry import build_reranker
from rag.retrievers.registry import build_retriever
from rag.technique import Technique


class Pipeline:
    """
    A retriever, then each step after it, then the context: what the RAG hands the Arbiter for a question. Each step
    reads what the step before hands on, as in the evaluation's tree.
    """

    def __init__(self, ranking_techniques: list[Technique], context: ContextBuilder, chunks: list[Chunk]) -> None:
        self._ranking_techniques = ranking_techniques
        self._context = context
        self._chunk_by_id: dict[str, Chunk] = {}
        for chunk in chunks:
            self._chunk_by_id[chunk.id] = chunk

    def retrieve_context(self, question: str) -> list[Chunk]:
        """The chunks of the context for `question`, in the order the context gives them."""
        handed_on: list[str] = []
        for technique in self._ranking_techniques:
            handed_on = technique.hand_on(technique.process(question, handed_on))
        context = []
        for chunk_id in self._context.build_context(question, handed_on):
            context.append(self._chunk_by_id[chunk_id])
        return context


def build_pipeline(layers: Layers, chunks: list[Chunk]) -> Pipeline:
    """The pipeline of the techniques `layers` names, such as the baseline's, built on the indexed `chunks`."""
    if layers.context is None:
        raise ValueError("The Arbiter reads a context: the layers of its pipeline must name one")
    return Pipeline(build_ranking_techniques(layers, chunks), build_context(layers.context, chunks), chunks)


def build_ranking_techniques(layers: Layers, chunks: list[Chunk]) -> list[Technique]:
    """The techniques that rank the chunks, before the context: the retriever, then the expander and the reranker
    `layers` names. Several pipelines may share them, so that a model loads once."""
    techniques: list[Technique] = [build_retriever(layers.retriever, chunks)]
    if layers.expand is not None:
        techniques.append(build_expander(layers.expand, chunks))
    if layers.rerank is not None:
        techniques.append(build_reranker(layers.rerank, chunks))
    return techniques
