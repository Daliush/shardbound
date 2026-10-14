"""
The evaluation (design doc §6.3). `evaluation_service.py` holds the whole flow: build what is evaluated, run it on
every question, score the results. `questions.py` reads the questions, `retrieval_metrics.py` scores one question's
ranking, `scores.py` averages, `report_table.py` prints. Today it measures the retrieval part of the Arbiter; the
answers will be scored here too once there is a generator, with their own metrics module.
"""
