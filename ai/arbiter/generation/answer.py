"""What the Arbiter answers: one schema for every question (design doc §5.2)."""

import hashlib
import json

from pydantic import BaseModel, ConfigDict, Field


class Answer(BaseModel):
    """`answer` is all the player reads, the citations shown as links; `citations` and `answerable` are checked by
    code against the engine's answer key."""

    model_config = ConfigDict(extra="forbid")

    answer: str = Field(description="The ruling, in plain words, for the player who asked.")
    citations: list[str] = Field(
        description="The ids of the rules and cards the ruling relies on, in the order they apply.")
    answerable: bool = Field(description="False when the context does not hold the answer.")


ANSWER_SCHEMA = Answer.model_json_schema()
"""The JSON schema a model's reply must follow: its three fields, each required, and no other."""

ANSWER_SCHEMA_HASH = hashlib.sha256(json.dumps(ANSWER_SCHEMA, sort_keys=True).encode("utf-8")).hexdigest()[:12]
"""The schema in a few characters: the model reads its field descriptions, so a changed schema is another setting."""
