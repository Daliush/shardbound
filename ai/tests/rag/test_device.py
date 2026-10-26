from rag.device import describe_device, read_device


def test_reads_the_cpu_when_rag_device_is_unset_and_records_the_torch_build(monkeypatch):
    monkeypatch.delenv("RAG_DEVICE", raising=False)
    monkeypatch.setattr("rag.device.version", lambda package: {"torch": "2.14.1+cpu"}[package])

    assert read_device() == "cpu"
    assert describe_device() == "cpu, torch 2.14.1+cpu"


def test_reads_the_device_rag_device_names(monkeypatch):
    monkeypatch.setenv("RAG_DEVICE", "cuda:1")

    assert read_device() == "cuda:1"
