"""Where the models run, the embedding model and the rerankers: the CPU, unless `RAG_DEVICE` names a GPU (`ai/README.md`
says how to set one up)."""

import os
from importlib.metadata import version

CPU = "cpu"


def read_device() -> str:
    """`RAG_DEVICE`, as torch names a device ("cpu", "cuda", "cuda:1"), or "cpu" when it is unset. An AMD GPU is "cuda"
    too: the ROCm build of torch keeps CUDA's name."""
    return os.environ.get("RAG_DEVICE", CPU)


def describe_device() -> str:
    """
    What an evaluation records of where its models ran: the device and the torch build it ran on, "cpu, torch
    2.14.1+cpu" or "AMD Radeon RX 6800 XT, torch 2.14.0+rocm10.1.0". A GPU build is installed apart from the locked one,
    so the commit alone does not say which torch ran, even on the CPU.
    """
    device = read_device()
    build = version("torch")
    if device == CPU:
        return f"{CPU}, torch {build}"
    # Imported here, not at the top: torch takes seconds to load, and a run on the CPU does not need it.
    import torch

    return f"{torch.cuda.get_device_name(torch.device(device))}, torch {build}"
