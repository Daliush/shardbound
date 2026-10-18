from rag.chunks import Chunk
from rag.expanders.refs.refs_expander import RefsExpander
from rag.expanders.registry import build_expander, list_expander_names

CHUNKS = [Chunk("neutral.tempest", "card", "Tempest", "Tempest: a card.", ("8.2",), "c", "h")]


def test_names_every_expander():
    assert list_expander_names() == ["refs"]


def test_builds_an_expander_by_its_name_and_gives_it_that_name():
    expander = build_expander("refs", CHUNKS)
    assert isinstance(expander, RefsExpander)
    assert expander.name == "refs"
