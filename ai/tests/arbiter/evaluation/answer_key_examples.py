"""Answer keys of v2's dev questions, as the engine and the card data wrote them, with the cards each question names:
the answers, in tests."""

from arbiter.evaluation.answer_keys import AnswerKey
from arbiter.generation.answer import Answer

ASH_WARDEN = AnswerKey(
    question_id="echo+modify/ember.ash-warden/ember.roaring-pyre/neutral.tempest", category="interaction",
    question="I have Ash Warden on my board, and my opponent has no unit. What happens if I cast Roaring Pyre and then "
             "cast Tempest?",
    answerable=True, citations=["8.5", "8.2", "11.1.1", "11.1.2", "8.1", "11.1.9", "8.18"],
    rules=["8.5", "8.2", "11.1.1", "11.1.2", "8.1"],
    named_cards=["ember.ash-warden", "ember.roaring-pyre", "neutral.tempest"],
    trace=["CardPlayed[6.3]", "Modified[8.5]", "SpellResolved[6.3, 3.5]", "CardPlayed[6.3]", "UnitDestroyed[8.2]",
           "SpellResolved[6.3, 3.5]", "EchoTriggered[11.1.1, 11.1.2]", "PlayerDamaged[8.1]"])

CINDERLING = AnswerKey(
    question_id="death+trigger-order/ember.cinderling/tide.shoal-sprite/neutral.tempest/20-hp", category="multi-step",
    question="My opponent has Cinderling, then Shoal Sprite, which arrived after Cinderling. I have 20 HP. What "
             "happens if I cast Tempest?",
    answerable=True, citations=["8.2", "6.3", "3.5", "9.3", "8.1", "8.6", "9.8", "9.11"],
    rules=["8.2", "6.3", "3.5", "9.3", "8.1", "8.6"],
    named_cards=["ember.cinderling", "tide.shoal-sprite", "ember.cinderling", "neutral.tempest"],
    trace=["CardPlayed[6.3]", "UnitDestroyed[8.2]", "UnitDestroyed[8.2]", "SpellResolved[6.3, 3.5]",
           "AbilityTriggered[9.3]", "PlayerDamaged[8.1]", "AbilityTriggered[9.3]", "CardDrawn[8.6]"])

BINDING_THREAD = AnswerKey(
    question_id="card/neutral.binding-thread/faction", category="factual",
    question="What faction is Binding Thread in?",
    answerable=True, citations=["neutral.binding-thread"], rules=[], named_cards=["neutral.binding-thread"],
    trace=[])

RAMPAGE = AnswerKey(
    question_id="unanswerable/keyword/rampage/1", category="unanswerable", question="What does the Rampage keyword do?",
    answerable=False, citations=[], rules=[], named_cards=[], trace=[])


def answer_citing(*citations: str, answerable: bool = True) -> Answer:
    return Answer(answer="…", citations=list(citations), answerable=answerable)
