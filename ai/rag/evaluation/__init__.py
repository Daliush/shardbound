"""
The evaluation (design doc §6.3). `evaluation_service.py` holds the whole flow: build what is evaluated, run it on
every question, score the results. `questions.py` reads the questions, `retrieval_metrics.py` holds the metrics that
score one question's ranking and `metric_description.py` what each says about itself, `scores.py` makes each
question's result and the scores of a group of questions, `report_table.py` prints. It measures the retrieval part of
the Arbiter; its answers are scored in `arbiter/evaluation`, with their own metrics.
"""
