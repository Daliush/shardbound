"""
The evaluation of the Arbiter's answers (design doc §6.3). `evaluation_service.py` holds the whole flow: the Arbiter
answers every question of a split, then code scores each answer against its key, with no model. `answer_keys.py`
reads the keys, `trace_steps.py` reads a scenario's trace, `answer_metrics.py` holds the metrics that score one answer,
each describing itself, `answer_scores.py` makes each question's result and the scores of a group of questions,
`evaluation_json.py` and `answer_evaluation_repository.py` save them, `report_table.py` prints. Nothing outside this
package imports it.
"""
