import pytest

from arbiter.arbiter import Reply
from arbiter.evaluation.answer_scores import (AnswerResult, list_metric_descriptions, report_answers, score_answer,
                                              score_group)
from arbiter.generation import Generation
from arbiter.generation.completion import Price, Usage
from arbiter.generation.prompt import ContextChunk
from tests.arbiter.evaluation.answer_key_examples import ASH_WARDEN, RAMPAGE, answer_citing

PRICE = Price(input_per_million=0.10, output_per_million=0.50)


def build_result(category: str, metric_values: dict[str, float | None], usage: Usage = Usage(1000, 200),
                 cost_dollars: float = 0.0002, seconds: float = 2.0) -> AnswerResult:
    return AnswerResult(question_id="q", category=category, context_ids=[], answer=None, format_failures=[],
                        metric_values=metric_values, usage=usage, cost_dollars=cost_dollars, seconds=seconds)


def values(valid: float, answered: float | None, refused: float | None, recall: float | None,
           precision: float | None, order: float | None) -> dict[str, float | None]:
    return {"valid_answer": valid, "answered": answered, "correct_refusal": refused, "citation_recall": recall,
            "citation_precision": precision, "step_order": order}


def test_scores_a_reply_with_every_metric_and_keeps_what_the_arbiter_read_answered_and_spent():
    context = [ContextChunk("8.2", "rule", "…"), ContextChunk("neutral.tempest", "card", "…")]
    answer = answer_citing("8.2", "8.5", "neutral.tempest")
    reply = Reply(ASH_WARDEN.question, context, Generation(answer, ["stopped on end_turn: …"], Usage(1600, 600), 4.2))

    result = score_answer(ASH_WARDEN, reply, PRICE)

    assert result.question_id == ASH_WARDEN.question_id
    assert result.category == "interaction"
    assert result.context_ids == ["8.2", "neutral.tempest"]
    assert result.answer == answer
    assert result.format_failures == ["stopped on end_turn: …"]
    assert result.metric_values == values(1.0, 1.0, None, 2 / 7, 1.0, 0.0)
    assert result.cost_dollars == PRICE.cost_of(Usage(1600, 600))
    assert result.seconds == 4.2


def test_averages_each_metric_over_the_questions_it_applies_to_and_counts_them():
    results = [build_result("interaction", values(1.0, 1.0, None, 0.5, 1.0, 0.5)),
               build_result("factual", values(1.0, 0.0, None, 1.0, 0.5, None)),
               build_result("unanswerable", values(1.0, None, 0.0, None, None, None))]

    scores = score_group(results)

    assert scores.question_count == 3
    assert scores.metric_means == {"valid_answer": 1.0, "answered": 0.5, "correct_refusal": 0.0,
                                   "citation_recall": 0.75, "citation_precision": 0.75, "step_order": 0.5}
    assert scores.metric_counts == {"valid_answer": 3, "answered": 2, "correct_refusal": 1, "citation_recall": 2,
                                    "citation_precision": 2, "step_order": 1}


def test_gives_no_mean_to_a_metric_that_applies_to_no_question_of_the_group():
    scores = score_group([build_result("unanswerable", values(1.0, None, 1.0, None, None, None))])

    assert scores.metric_means["citation_recall"] is None
    assert scores.metric_counts["citation_recall"] == 0


def test_averages_the_tokens_and_the_cost_and_reads_the_latency_from_each_question_s_time():
    results = [build_result("factual", values(1.0, 1.0, None, 1.0, 1.0, None), Usage(1000, 100), 0.0001, 1.0),
               build_result("factual", values(1.0, 1.0, None, 1.0, 1.0, None), Usage(3000, 300), 0.0003, 3.0)]

    scores = score_group(results)

    assert scores.mean_input_tokens == 2000
    assert scores.mean_output_tokens == 200
    assert scores.mean_cost_dollars == pytest.approx(0.0002)
    assert scores.latency_p50_seconds == 1.0
    assert scores.latency_p95_seconds == 3.0


def test_reports_all_the_questions_then_each_category_by_name():
    reply = Reply(RAMPAGE.question, [], Generation(answer_citing(answerable=False), [], Usage(500, 50), 1.0))
    results = [score_answer(ASH_WARDEN, Reply(ASH_WARDEN.question, [], Generation(None, [], Usage(0, 0), 0.0)), PRICE),
               score_answer(RAMPAGE, reply, PRICE)]

    report = report_answers(results)

    assert report.overall.question_count == 2
    assert list(report.by_category) == ["interaction", "unanswerable"]
    assert report.questions == results


def test_describes_each_metric_then_how_many_questions_it_scored_then_the_values_of_a_group():
    paths = []
    for description in list_metric_descriptions():
        paths.append(description.path)

    assert paths[:6] == ["mean_valid_answer", "mean_answered", "mean_correct_refusal", "mean_citation_recall",
                         "mean_citation_precision", "mean_step_order"]
    assert paths[6:12] == ["scored_valid_answer", "scored_answered", "scored_correct_refusal", "scored_citation_recall",
                           "scored_citation_precision", "scored_step_order"]
    assert paths[12:] == ["question_count", "mean_input_tokens", "mean_output_tokens", "mean_cost_dollars",
                          "latency_p50_seconds", "latency_p95_seconds"]
