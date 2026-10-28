"""
The baseline: the pipeline the Arbiter would serve, and the one CI checks against main on every pull request
(`.github/workflows/rag-gate.yml`). It must run on the CPU in a few minutes, since CI and Cloud Run have no GPU: a
slower pipeline is a variant.
"""

from rag.evaluation.runs import Layers

BASELINE = Layers(retriever="rrf-bm25-dense", expand="refs", rerank="minilm-l6")
