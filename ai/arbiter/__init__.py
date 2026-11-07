"""
The Arbiter, the rules referee (design doc §5.2): `arbiter.py` asks the RAG for a question's context and the
generator for the answer, `generation/` writes the answer, and `evaluation/` scores the answers, with its own command
(`python -m arbiter.evaluation`). Nothing outside `evaluation/` imports it: the Arbiter a player talks to carries no
evaluation code.
"""
