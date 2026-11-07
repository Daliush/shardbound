from arbiter.generation.claude.claude_generator import ClaudeGenerator
from arbiter.generation.completion import Price
from arbiter.generation.registry import BASELINE_GENERATOR, build_generator, list_generator_names


def test_builds_every_generator_it_names_without_a_key(monkeypatch):
    monkeypatch.delenv("CLAUDE_KEY", raising=False)

    for name in list_generator_names():
        assert build_generator(name).name == name


def test_the_baseline_generator_is_claude_haiku_at_medium_effort_and_its_list_price():
    generator = build_generator(BASELINE_GENERATOR)

    assert isinstance(generator, ClaudeGenerator)
    assert generator.settings["model"] == "claude-haiku-5-5"
    assert generator.settings["effort"] == "medium"
    assert generator.price == Price(input_per_million=0.10, output_per_million=0.50)
