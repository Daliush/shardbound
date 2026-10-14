"""The BM25 variants the evaluation runs: the baseline, then one per setting it changes (design doc §6.2)."""

from rag.retrievers.bm25.bm25_retriever import Bm25Settings

BM25_VARIANTS: dict[str, Bm25Settings] = {
    "bm25": Bm25Settings(),
    "bm25-no-stemming": Bm25Settings(stemming=False),
    "bm25-stop-words-removed": Bm25Settings(stop_words_removed=True),
    "bm25-robertson": Bm25Settings(formula="robertson"),
    "bm25-atire": Bm25Settings(formula="atire"),
    "bm25l": Bm25Settings(formula="bm25l"),
    "bm25+": Bm25Settings(formula="bm25+"),
}
