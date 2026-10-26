"""
What CI asks of a pull request about retrieval (`.github/workflows/rag-gate.yml`). `retrieval_inputs.py` lists the
files an evaluation's numbers depend on, `git_history.py` asks git what the pull request changed, and
`fresh_evaluation.py` checks that a pull request changing those files commits an evaluation of its own code.
"""
