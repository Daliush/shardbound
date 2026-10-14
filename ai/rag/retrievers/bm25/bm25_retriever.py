"""BM25 (design doc §5.2): the chunks that share the most words with the question, rare words weighing more."""

from dataclasses import dataclass

import bm25s
import Stemmer

from rag.chunks import Chunk
from rag.retrievers import Retriever


@dataclass(frozen=True)
class Bm25Settings:
    """The baseline, then each variant changes one setting: the formula, the stemming or the stop words."""

    formula: str = "lucene"
    """"lucene", "robertson", "atire", "bm25l" or "bm25+", as bm25s names them."""
    stemming: bool = True
    """"destroyed" and "Destroy" both become "destroy"."""
    stop_words_removed: bool = False
    """Drops "the", "a", "of"…, but also "not" and "no", which rules depend on."""
    k1: float = 1.5
    b: float = 0.75


class Bm25Retriever(Retriever):
    """Indexes the chunks once; each question then only reads the index."""

    def __init__(self, chunks: list[Chunk], settings: Bm25Settings = Bm25Settings()) -> None:
        self._settings = settings
        self._stemmer = Stemmer.Stemmer("english") if settings.stemming else None
        self._indexed_chunk_ids = [chunk.id for chunk in chunks]
        """bm25s knows a chunk only by its position in the index; this list gives back its id."""
        self._index = bm25s.BM25(method=settings.formula, k1=settings.k1, b=settings.b)
        self._index.index([self._split_into_words(chunk.text) for chunk in chunks], show_progress=False)

    def rank_chunks(self, question: str) -> list[str]:
        """Only the chunks sharing at least one word with the question: the others score 0, BM25 found nothing."""
        scores = self._index.get_scores(self._split_into_words(question))
        score_by_chunk_id = {}
        for position, chunk_id in enumerate(self._indexed_chunk_ids):
            if scores[position] > 0:
                score_by_chunk_id[chunk_id] = scores[position]
        return sorted(score_by_chunk_id, key=score_by_chunk_id.get, reverse=True)

    def _split_into_words(self, text: str) -> list[str]:
        """Lowercase words, stemmed or not, stop words kept or not: the same for the chunks and the question."""
        return bm25s.tokenize([text], stopwords="english" if self._settings.stop_words_removed else None,
                              stemmer=self._stemmer, return_ids=False, show_progress=False)[0]
