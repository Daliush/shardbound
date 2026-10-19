"""
Comparing two saved evaluations of one dataset split, the `compare` command: `comparison_service.py` matches their
runs by layers and says what moved, `comparison_table.py` prints it. CI compares a fresh evaluation with the one its
commit records, so that a change that lowers recall shows in the pull request.
"""
