"""Where the rerankers run: the CPU, unless `RAG_DEVICE` names a GPU (`ai/README.md` says how to set one up)."""

import os

CPU = "cpu"


def read_device() -> str:
    """`RAG_DEVICE`, as torch names a device ("cpu", "cuda", "cuda:1"), or "cpu" when it is unset. An AMD GPU is "cuda"
    too: the ROCm build of torch keeps CUDA's name."""
    return os.environ.get("RAG_DEVICE", CPU)


def describe_device() -> str:
    """
    What an evaluation records of where its rerankers ran: "cpu", or the GPU's name and the torch build it ran on
    ("AMD Radeon RX 6800 XT, torch 2.14.0+rocm10.1.0"). A GPU build is installed over the locked one, so the commit
    alone does not say which torch ran.
    """
    device = read_device()
    if device == CPU:
        return CPU
    # Imported here, not at the top: torch takes seconds to load, and a run on the CPU does not need it.
    import torch

    return f"{torch.cuda.get_device_name(torch.device(device))}, torch {torch.__version__}"
