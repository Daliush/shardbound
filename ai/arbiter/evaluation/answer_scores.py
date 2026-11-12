"""The result of each question, and the scores of a group of them: the answer metrics, the tokens, the cost and the
time."""

from dataclasses import dataclass

from arbiter.arbiter import Reply
from arbiter.evaluation.answer_keys import AnswerKey
from arbiter.evaluation.answer_metrics import ANSWER_METRICS
from arbiter.generation.answer import Answer
from arbiter.generation.completion import Price, Usage
from rag.evaluation.metric_description import Display, MetricDescription
from rag.evaluation.scores import find_percentile

LATENCY_MEANING = ("How long a player waits for the answer once the context is chosen, as the model's API took when "
                   "the answer was first written. Lower is better.")

GROUP_VALUE_DESCRIPTIONS = [
    MetricDescription(
        path="question_count", name="Questions", display=Display.COUNT,
        what="How many questions were asked.",
        how="Every question of the split, or of one category, those that have no answer to give included.",
        represents="How much to trust a score. The fewer the questions, the more a single question moves it."),
    MetricDescription(
        path="mean_input_tokens", name="Input tokens", display=Display.COUNT,
        what="How many tokens the model reads for a question, on average.",
        how="Add up the input tokens of every try at a question, then take the average over all the questions.",
        represents="Most of it is the context: it grows with every chunk the prompt holds."),
    MetricDescription(
        path="mean_output_tokens", name="Output tokens", display=Display.COUNT,
        what="How many tokens the model writes for a question, on average, its thinking included.",
        how="Add up the output tokens of every try at a question, then take the average over all the questions.",
        represents="How much the model writes and thinks, which the latency and the cost follow."),
    MetricDescription(
        path="mean_cost_dollars", name="Cost", display=Display.DOLLARS,
        what="What a question costs, on average, in US dollars.",
        how="For each question, the tokens of every try at the model's list price per million tokens, input and "
            "output apart. Then take the average over all the questions.",
        represents="What an answer costs to serve. Lower is better."),
    MetricDescription(
        path="latency_p50_seconds", name="Latency p50", display=Display.SECONDS,
        what="How long a typical answer takes.",
        how="For each question, add up the time of every try. The p50 is the time that half of the questions don't "
            "go over.",
        represents=LATENCY_MEANING),
    MetricDescription(
        path="latency_p95_seconds", name="Latency p95", display=Display.SECONDS,
        what="How long the slowest answers take.",
        how="For each question, add up the time of every try. The p95 is the time that 95% of the questions don't "
            "go over.",
        represents=LATENCY_MEANING),
]
"""The values of a group that are not the mean of a metric, which `score_group` computes itself."""


@dataclass(frozen=True)
class AnswerResult:
    """One question's result: what the Arbiter read and answered, each metric's value, and what the answer cost."""

    question_id: str
    category: str
    context_ids: list[str]
    """The chunks of the prompt, in the order the prompt gives them."""
    answer: Answer | None
    format_failures: list[str]
    metric_values: dict[str, float | None]
    """Each answer metric's value for this question, by the metric's key, None when it does not apply."""
    usage: Usage
    cost_dollars: float
    seconds: float
    """How long every try at the model took."""


@dataclass(frozen=True)
class AnswerScores:
    """The scores of a group of questions: all the questions of a run, or those of one category."""

    question_count: int
    metric_means: dict[str, float | None]
    """Each answer metric averaged over the questions it applies to, by the metric's key; None when it applies to
    none of them."""
    metric_counts: dict[str, int]
    """How many questions each answer metric scored, by the metric's key."""
    mean_input_tokens: float
    mean_output_tokens: float
    mean_cost_dollars: float
    latency_p50_seconds: float
    latency_p95_seconds: float


@dataclass(frozen=True)
class AnswerReport:
    overall: AnswerScores
    by_category: dict[str, AnswerScores]
    questions: list[AnswerResult]


def list_metric_descriptions() -> list[MetricDescription]:
    """Every value the scores of a group hold, described: each answer metric, how many questions it scored, then the
    values only a group has."""
    descriptions = []
    for metric in ANSWER_METRICS:
        descriptions.append(metric.description)
    for metric in ANSWER_METRICS:
        descriptions.append(describe_metric_count(metric.description))
    descriptions.extend(GROUP_VALUE_DESCRIPTIONS)
    return descriptions


def describe_metric_count(description: MetricDescription) -> MetricDescription:
    """What the number of questions a metric scored says, a value of every group."""
    return MetricDescription(
        path=f"scored_{description.path.removeprefix('mean_')}", name=f"{description.name}: questions",
        display=Display.COUNT,
        what=f"How many questions {description.name} scored.",
        how=f"The questions {description.name} applies to, as its own description says.",
        represents=f"How much to trust {description.name}. The fewer the questions, the more a single question moves "
                   f"it.")


def score_answer(answer_key: AnswerKey, reply: Reply, price: Price) -> AnswerResult:
    """One question's result: each answer metric's value for the reply, and what the reply cost."""
    generation = reply.generation
    metric_values = {}
    for metric in ANSWER_METRICS:
        metric_values[metric.key] = metric.score(answer_key, generation.answer)
    context_ids = []
    for chunk in reply.context:
        context_ids.append(chunk.id)
    return AnswerResult(question_id=answer_key.question_id, category=answer_key.category, context_ids=context_ids,
                        answer=generation.answer, format_failures=generation.format_failures,
                        metric_values=metric_values, usage=generation.usage,
                        cost_dollars=price.cost_of(generation.usage), seconds=generation.seconds)


def report_answers(results: list[AnswerResult]) -> AnswerReport:
    """The scores of all the questions and of each category, and every question's result."""
    results_by_category: dict[str, list[AnswerResult]] = {}
    for result in results:
        results_by_category.setdefault(result.category, []).append(result)
    by_category = {}
    for category in sorted(results_by_category):
        by_category[category] = score_group(results_by_category[category])
    return AnswerReport(overall=score_group(results), by_category=by_category, questions=results)


def score_group(results: list[AnswerResult]) -> AnswerScores:
    """
    The scores of a group of questions. Each answer metric is averaged over the questions it applies to, every
    question weighing the same. The tokens and the cost are averaged over every question, and the latency p50 and p95
    read from the time each question took.
    """
    metric_means = {}
    metric_counts = {}
    for metric in ANSWER_METRICS:
        values = list_metric_values(results, metric.key)
        metric_means[metric.key] = sum(values) / len(values) if values else None
        metric_counts[metric.key] = len(values)
    seconds = []
    input_tokens = 0
    output_tokens = 0
    cost_dollars = 0.0
    for result in results:
        seconds.append(result.seconds)
        input_tokens += result.usage.input_tokens
        output_tokens += result.usage.output_tokens
        cost_dollars += result.cost_dollars
    return AnswerScores(question_count=len(results), metric_means=metric_means, metric_counts=metric_counts,
                        mean_input_tokens=input_tokens / len(results), mean_output_tokens=output_tokens / len(results),
                        mean_cost_dollars=cost_dollars / len(results),
                        latency_p50_seconds=find_percentile(seconds, 50),
                        latency_p95_seconds=find_percentile(seconds, 95))


def list_metric_values(results: list[AnswerResult], key: str) -> list[float]:
    """The metric's value for each question it applies to."""
    values = []
    for result in results:
        value = result.metric_values[key]
        if value is not None:
            values.append(value)
    return values
