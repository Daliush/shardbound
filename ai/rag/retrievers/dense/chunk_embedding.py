"""A chunk with the vector one model made from its text: what the dense index stores."""

from dataclasses import dataclass

import numpy as np

from rag.chunks import Chunk


@dataclass(frozen=True)
class ChunkEmbedding:
    chunk: Chunk
    vector: np.ndarray
