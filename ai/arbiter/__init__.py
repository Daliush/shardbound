"""
The Arbiter, the rules referee (design doc §5.2): `arbiter.py` asks the RAG for a question's context and the
generator for the answer, `generation/` writes the answer, and its evaluation will come in `evaluation/`. Nothing
outside `evaluation/` imports it: the Arbiter a player talks to carries no evaluation code.
"""
