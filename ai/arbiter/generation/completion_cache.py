"""The completions kept on disk, in `ai/.cache/arbiter/`: a rerun costs nothing and reads the same answers."""

import hashlib
import json
from dataclasses import asdict
from pathlib import Path

from arbiter.generation.completion import Completion, Usage
from arbiter.generation.prompt import Prompt

CACHE_FOLDER = Path("ai") / ".cache" / "arbiter"


class CompletionCache:
    """
    One file per completion, found by the generator's settings, the prompt and the attempt: a second attempt at the
    same prompt is another call, so it gets its own file.
    """

    def __init__(self, folder: Path) -> None:
        self._folder = folder

    def find_completion(self, settings: dict[str, str | int], prompt: Prompt, attempt: int) -> Completion | None:
        file = self._locate(settings, prompt, attempt)
        if not file.exists():
            return None
        record = json.loads(file.read_text(encoding="utf-8"))
        return Completion(record["text"], record["stop_reason"], Usage(**record["usage"]))

    def save_completion(self, settings: dict[str, str | int], prompt: Prompt, attempt: int,
                        completion: Completion) -> None:
        file = self._locate(settings, prompt, attempt)
        file.parent.mkdir(parents=True, exist_ok=True)
        record = {"settings": settings, "system": prompt.system, "user": prompt.user, "attempt": attempt,
                  "text": completion.text, "stop_reason": completion.stop_reason, "usage": asdict(completion.usage)}
        file.write_text(json.dumps(record, ensure_ascii=False, indent=1), encoding="utf-8")

    def _locate(self, settings: dict[str, str | int], prompt: Prompt, attempt: int) -> Path:
        identity = [settings, prompt.system, prompt.user, attempt]
        key = hashlib.sha256(json.dumps(identity, sort_keys=True).encode("utf-8")).hexdigest()
        return self._folder / key[:2] / f"{key}.json"
