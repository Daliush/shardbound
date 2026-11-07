from arbiter.generation.completion import Completion, Usage
from arbiter.generation.completion_cache import CompletionCache
from arbiter.generation.prompt import Prompt

SETTINGS = {"model": "claude-haiku-5-5", "effort": "medium", "max_tokens": 16000}
PROMPT = Prompt("You are the rules referee.", "<question>What does Tempest do?</question>")
COMPLETION = Completion('{"answer": "It destroys every unit."}', "end_turn", Usage(1200, 300), 2.4)


def test_finds_the_completion_it_saved_with_the_time_its_call_took(tmp_path):
    cache = CompletionCache(tmp_path)

    cache.save_completion(SETTINGS, PROMPT, 1, COMPLETION)

    assert CompletionCache(tmp_path).find_completion(SETTINGS, PROMPT, 1) == COMPLETION


def test_finds_nothing_for_another_attempt_prompt_or_setting(tmp_path):
    cache = CompletionCache(tmp_path)
    cache.save_completion(SETTINGS, PROMPT, 1, COMPLETION)

    assert cache.find_completion(SETTINGS, PROMPT, 2) is None
    assert cache.find_completion(SETTINGS, Prompt(PROMPT.system, "<question>And Brine Ward?</question>"), 1) is None
    assert cache.find_completion({**SETTINGS, "effort": "high"}, PROMPT, 1) is None
