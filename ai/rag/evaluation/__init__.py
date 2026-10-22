"""
The evaluation (design doc §6.3). `evaluation_service.py` holds the whole flow: build what is evaluated, run it on
every question, score the results. `questions.py` reads the questions, `retrieval_metrics.py` holds the metrics that
score one question's ranking and `metric_description.py` what each says about itself, `scores.py` makes each
question's result and the scores of a group of questions, `report_table.py` prints. Today it measures the retrieval
part of the Arbiter; the answers will be scored here too once there is a generator, with their own metrics module.
"""
