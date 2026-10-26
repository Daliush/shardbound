"""The second reranker: Qwen3-Reranker-0.6B, a cross-encoder built on the Qwen3 LLM and told what the task is."""

from rag.rerankers.cross_encoder_reranker import CrossEncoderReranker


class Qwen3Reranker(CrossEncoderReranker):
    """
    `Qwen/Qwen3-Reranker-0.6B` (600M parameters, about 27 times MiniLM-L6) is a small LLM trained to answer "yes" or
    "no": does the document meet the instruction, for this query? Its score is the logit of "yes" minus the logit of
    "no", which orders the candidates as the probability of "yes" would. The instruction says what the task is: the
    model's own is about web search. The revision is pinned: the repository was changed after its release so that
    sentence-transformers loads it, and the instruction only reaches the model through that configuration.
    """

    HUGGING_FACE_ID = "Qwen/Qwen3-Reranker-0.6B"
    REVISION = "e61197ed45024b0ed8a2d74b80b4d909f1255473"
    INSTRUCTION = "Given a question about a card game's rules, retrieve the rules and cards needed to answer it."

    @property
    def description(self) -> str:
        return f'reranked by Qwen3-Reranker-0.6B, a cross-encoder built on an LLM, told "{self.INSTRUCTION}"'

    def _load_model(self):
        # The model's configuration puts its "query" prompt, the instruction, into its chat template.
        return self._load_cross_encoder(self.HUGGING_FACE_ID, revision=self.REVISION,
                                        prompts={"query": self.INSTRUCTION})
