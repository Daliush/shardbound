"""
The answer metrics of one question: what code checks in an answer against its answer key, with no model (design doc
§6.3). Each scores one answer from 0 to 1, or gives None for a question it does not apply to, which the mean of a
group leaves out; each says what it does, how and what it represents, like the retrieval metrics. `ANSWER_METRICS`
lists every metric the evaluation scores.
"""

from abc import ABC, abstractmethod

from arbiter.evaluation.answer_keys import AnswerKey
from arbiter.evaluation.trace_steps import find_steps, list_trace_rules
from arbiter.generation.answer import Answer
from rag.evaluation.metric_description import Display, MetricDescription


class AnswerMetric(ABC):

    def __init__(self, key: str, name: str, what: str, how: str, represents: str) -> None:
        self.key = key
        """Where its value is in a question's result: `citation_recall`. A group's scores hold its mean at `mean_` and
        the key."""
        self.description = MetricDescription(path=f"mean_{key}", name=name, display=Display.SHARE, what=what, how=how,
                                             represents=represents)

    @abstractmethod
    def score(self, answer_key: AnswerKey, answer: Answer | None) -> float | None:
        """Its value for one question, None when it does not apply to it. `answer` is None when no attempt gave a
        valid answer."""


class ValidAnswer(AnswerMetric):
    def __init__(self) -> None:
        super().__init__(
            key="valid_answer", name="Valid answers",
            what="Out of the questions, the share that got an answer in the expected format.",
            how="A reply is an answer when it holds a text, the ids it cites and whether the question can be answered, "
                "and nothing else. A reply that is not gets one more try. A question counts when one of its two tries "
                "is an answer. Then take the average over all the questions.",
            represents="Whether the player gets an answer at all. A question with no answer also gets 0 on "
                       "answered or correct refusals, and on citation recall.")

    def score(self, answer_key: AnswerKey, answer: Answer | None) -> float | None:
        return 1.0 if answer is not None else 0.0


class Answered(AnswerMetric):
    def __init__(self) -> None:
        super().__init__(
            key="answered", name="Answered",
            what="Out of the questions that have an answer, the share the Arbiter answers instead of refusing.",
            how="An answer refuses when it says the question can't be answered (\"answerable\" is false). Count 1 for "
                "an answer that does not refuse, 0 for one that does or for no answer. Questions that have no answer "
                "to give are left out. Then take the average over the questions.",
            represents="How rarely the Arbiter refuses by mistake: 100% means it never does. It says nothing of "
                       "whether the answer is right, nor of the questions it should refuse: read it with correct "
                       "refusals.")

    def score(self, answer_key: AnswerKey, answer: Answer | None) -> float | None:
        if not answer_key.answerable:
            return None
        return 1.0 if answer is not None and answer.answerable else 0.0


class CorrectRefusal(AnswerMetric):
    def __init__(self) -> None:
        super().__init__(
            key="correct_refusal", name="Correct refusals",
            what="Out of the questions that have no answer, the share the Arbiter refuses.",
            how="Count 1 for an answer that says the question can't be answered (\"answerable\" is false), 0 for one "
                "that answers it or for no answer. Only the questions that have no answer to give are scored. Then "
                "take the average over them.",
            represents="How rarely the Arbiter invents an answer: an answer to a question that has none can only "
                       "be made up. Read it with answered, which a model that always refuses would fail.")

    def score(self, answer_key: AnswerKey, answer: Answer | None) -> float | None:
        if answer_key.answerable:
            return None
        return 1.0 if answer is not None and not answer.answerable else 0.0


class CitationRecall(AnswerMetric):
    def __init__(self) -> None:
        super().__init__(
            key="citation_recall", name="Citation recall",
            what="Out of the rules and cards an answer must cite, the share it cites.",
            how="The answer key lists what a correct answer cites. Count how many of them the answer cites, then "
                "divide by how many there are: 2 out of 7 gives 29%. A question with no answer gets 0. Questions that "
                "have no answer to give are left out. Then take the average over the questions.",
            represents="Whether an answer cites everything it relies on. It does not look at what else the answer "
                       "cites: read it with citation precision.")

    def score(self, answer_key: AnswerKey, answer: Answer | None) -> float | None:
        if not answer_key.answerable:
            return None
        cited = set(answer.citations) if answer is not None else set()
        found = 0
        for citation in answer_key.citations:
            if citation in cited:
                found += 1
        return found / len(answer_key.citations)


class CitationPrecision(AnswerMetric):
    def __init__(self) -> None:
        super().__init__(
            key="citation_precision", name="Citation precision",
            what="Out of the ids an answer cites that are right or wrong, the share that are right.",
            how="A cited id is right when the answer key lists it. Two kinds are tolerated, and neither counted right "
                "nor wrong: a rule an event of the scenario carries without being a key event (a spell going to the "
                "graveyard), and a card the question names. Any other id is wrong. Divide the right ones by the right "
                "and the wrong ones: 1 right and 1 wrong gives 50%. Answers that cite only tolerated ids or nothing, "
                "and questions that have no answer to give, are left out. Then take the average over the questions.",
            represents="Whether an answer cites only what it should. Citing the cards the question names neither "
                       "helps nor hurts. It does not look at what the answer leaves out: read it with citation "
                       "recall.")

    def score(self, answer_key: AnswerKey, answer: Answer | None) -> float | None:
        if not answer_key.answerable or answer is None:
            return None
        tolerated_ids = list_trace_rules(answer_key.trace) | set(answer_key.named_cards)
        right = 0
        wrong = 0
        for citation in dict.fromkeys(answer.citations):
            if citation in answer_key.citations:
                right += 1
            elif citation not in tolerated_ids:
                wrong += 1
        return right / (right + wrong) if right + wrong else None


class StepOrder(AnswerMetric):
    def __init__(self) -> None:
        super().__init__(
            key="step_order", name="Step order",
            what="Whether an answer cites the rules of a scenario in the order they apply.",
            how="A scenario's key events happen one after the other, each with its rules. Take every pair of cited "
                "rules from two different events, then count the pairs cited in the order the events happen: 5 out of "
                "6 gives 83%. Only scenarios are scored, when the answer cites rules of at least two of their events. "
                "Then take the average over the questions.",
            represents="Whether an answer gives a chain of effects in the right order. One rule out of place costs "
                       "a few pairs, not the whole score.")

    def score(self, answer_key: AnswerKey, answer: Answer | None) -> float | None:
        if not answer_key.trace or answer is None:
            return None
        cited_steps = list_cited_steps(find_steps(answer_key.rules, answer_key.trace), answer.citations)
        pairs = 0
        in_order = 0
        for first in range(len(cited_steps)):
            for later in range(first + 1, len(cited_steps)):
                if cited_steps[first] != cited_steps[later]:
                    pairs += 1
                    if cited_steps[first] < cited_steps[later]:
                        in_order += 1
        return in_order / pairs if pairs else None


def list_cited_steps(steps: list[list[str]], citations: list[str]) -> list[int]:
    """The step of each essential rule the answer cites, in the order it cites them, a rule cited twice counting at its
    first place: citing 8.2, then 8.5, then 8.1, of [["8.5"], ["8.2"], ["8.1"]], gives [1, 0, 2]."""
    step_of_rule = {}
    for index, step in enumerate(steps):
        for rule in step:
            step_of_rule[rule] = index
    cited_steps = []
    for citation in dict.fromkeys(citations):
        if citation in step_of_rule:
            cited_steps.append(step_of_rule[citation])
    return cited_steps


ANSWER_METRICS: list[AnswerMetric] = [ValidAnswer(), Answered(), CorrectRefusal(), CitationRecall(),
                                      CitationPrecision(), StepOrder()]
"""Every answer metric the evaluation scores, in the order a report shows them."""
