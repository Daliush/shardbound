"""The first reranker (design doc §5.2): MiniLM-L6, a small cross-encoder trained on web search queries."""

from rag.rerankers.cross_encoder_reranker import CrossEncoderReranker


class MiniLMReranker(CrossEncoderReranker):
    """`cross-encoder/ms-marco-MiniLM-L6-v2` (22M parameters): it reads the question and a text together and outputs
    one score."""

    HUGGING_FACE_ID = "cross-encoder/ms-marco-MiniLM-L6-v2"

    @property
    def description(self) -> str:
        return "reranked by MiniLM-L6, a cross-encoder that reads the question with each candidate"

    def _load_model(self):
        return self._load_cross_encoder(self.HUGGING_FACE_ID)
