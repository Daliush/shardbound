from arbiter.arbiter import Arbiter
from arbiter.generation.prompt import ContextChunk
from rag.chunks import Chunk
from rag.contexts.named_cards.named_cards_context import NamedCardsContext
from rag.pipeline import Pipeline
from tests.arbiter.generation.scripted_generator import VALID_REPLY, ScriptedGenerator
from tests.rag.retrievers.fixed_ranking import FixedRanking

CHUNKS = [Chunk("8.2", "rule", None, "[8. Effects] **Destroy**: board to graveyard.", (), "r", "h"),
          Chunk("neutral.tempest", "card", "Tempest", "Tempest: Destroy all units.", ("8.2",), "c", "h")]
QUESTION = "What does Tempest do?"


def test_answers_from_the_context_its_pipeline_retrieves():
    generator = ScriptedGenerator([VALID_REPLY])
    context = NamedCardsContext("named-cards", CHUNKS, follow_refs=False)
    arbiter = Arbiter(Pipeline([FixedRanking(["8.2"])], context, CHUNKS), generator)

    reply = arbiter.answer_question(QUESTION)

    assert reply.context == [ContextChunk("8.2", "rule", "[8. Effects] **Destroy**: board to graveyard."),
                             ContextChunk("neutral.tempest", "card", "Tempest: Destroy all units.")]
    assert '<card id="neutral.tempest">' in generator.prompts[0].user
    assert reply.generation.answer.citations == ["8.2"]


def test_answers_from_no_context_without_a_pipeline():
    generator = ScriptedGenerator([VALID_REPLY])

    reply = Arbiter(None, generator).answer_question(QUESTION)

    assert reply.context == []
    assert generator.prompts[0].user.startswith("<context>\n\n</context>")
