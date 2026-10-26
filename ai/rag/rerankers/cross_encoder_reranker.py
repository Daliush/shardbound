"""What every reranker has in common so far: a cross-encoder that scores the question with each candidate."""

from abc import abstractmethod
from functools import cached_property

from rag.chunks import Chunk
from rag.device import read_device
from rag.rerankers import Reranker


class CrossEncoderReranker(Reranker):
    """
    Reads the question together with each candidate's text, scores how well the text answers it, and orders the
    candidates by that score. Each reranker loads its own model, a sentence-transformers `CrossEncoder`, downloaded
    from Hugging Face on first use, then read from its cache.
    """

    def __init__(self, name: str, chunks: list[Chunk]) -> None:
        super().__init__(name)
        self._text_by_chunk_id: dict[str, str] = {}
        for chunk in chunks:
            self._text_by_chunk_id[chunk.id] = chunk.text

    def rerank_chunks(self, question: str, chunk_ids: list[str]) -> list[str]:
        texts = []
        for chunk_id in chunk_ids:
            texts.append(self._text_by_chunk_id[chunk_id])
        reranked_ids = []
        # Each hit names its candidate by its position in `texts`, the highest score first; on a tie, the retriever's
        # order stays.
        for hit in self._model.rank(question, texts):
            reranked_ids.append(chunk_ids[hit["corpus_id"]])
        return reranked_ids

    @cached_property
    def _model(self):
        """Loaded on the first question to rerank, not before: building the reranker costs nothing."""
        return self._load_model()

    @abstractmethod
    def _load_model(self):
        """The reranker's `CrossEncoder`, from `_load_cross_encoder`."""

    def _load_cross_encoder(self, hugging_face_id: str, **settings):
        """
        The `CrossEncoder` that `hugging_face_id` names, on the device `RAG_DEVICE` names, in float32: the format in
        which the CPU and a GPU gave the same rankings on 6 dev questions, however many candidates were scored at once.
        In bfloat16, the format Qwen3-Reranker's weights are stored in, the batch size alone moves its scores by about
        0.07.
        """
        # Imported here, not at the top: torch takes seconds to load, and BM25 runs and the tests never need it.
        from sentence_transformers import CrossEncoder

        return CrossEncoder(hugging_face_id, device=read_device(), model_kwargs={"dtype": "float32"}, **settings)
