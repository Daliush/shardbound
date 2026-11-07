"""The pipeline the Arbiter calls: the techniques a run's layers name, one after the other, then the context."""

from rag.chunks import Chunk
from rag.context import ContextSelector
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

    def __init__(self, techniques: list[Technique], context: ContextSelector, chunks: list[Chunk]) -> None:
        self._techniques = techniques
        self._context = context
        self._chunk_by_id: dict[str, Chunk] = {}
        for chunk in chunks:
            self._chunk_by_id[chunk.id] = chunk

    def retrieve_context(self, question: str) -> list[Chunk]:
        """The chunks of the context for `question`, in the order the context selector gives them."""
        ranked: list[str] = []
        handed_on: list[str] = []
        for technique in self._techniques:
            ranked = technique.process(question, handed_on)
            handed_on = technique.hand_on(ranked)
        context = []
        for chunk_id in self._context.select_context(question, ranked):
            context.append(self._chunk_by_id[chunk_id])
        return context


def build_pipeline(layers: Layers, chunks: list[Chunk]) -> Pipeline:
    """The pipeline of the techniques `layers` names, such as the baseline's, built on the indexed `chunks`."""
    techniques: list[Technique] = [build_retriever(layers.retriever, chunks)]
    if layers.expand is not None:
        techniques.append(build_expander(layers.expand, chunks))
    if layers.rerank is not None:
        techniques.append(build_reranker(layers.rerank, chunks))
    return Pipeline(techniques, ContextSelector(chunks), chunks)
