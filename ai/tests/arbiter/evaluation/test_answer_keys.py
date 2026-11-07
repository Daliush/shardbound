import json

from arbiter.evaluation.answer_keys import AnswerKey, read_answer_keys
from rag.chunks import Chunk
from rag.named_cards import NamedCardFinder

ROW = {"id": "card/neutral.tempest/cost", "category": "factual", "group": "card/neutral.tempest", "split": "dev",
       "question": "How many Shards does Tempest cost?", "template_question": "What does Tempest cost?",
       "wording": "paraphrase", "answerable": True, "source": "card-data", "key_facts": ["Tempest costs 7 Shards."],
       "citations": ["neutral.tempest"], "rules": [], "context_rules": [], "cards": ["neutral.tempest"], "trace": []}
CHUNKS = [Chunk("neutral.tempest", "card", "Tempest", "Tempest: Neutral spell.", ("8.2",), "c", "h"),
          Chunk("8.2", "rule", None, "[8. Effects] **Destroy**.", (), "r", "h")]


def test_reads_each_question_of_the_split_with_what_the_answer_metrics_need_and_the_cards_it_names(tmp_path):
    folder = tmp_path / "evals" / "datasets" / "v2"
    folder.mkdir(parents=True)
    (folder / "dataset-dev.jsonl").write_text(json.dumps(ROW) + "\n", encoding="utf-8")

    assert read_answer_keys(tmp_path, "v2", "dev", NamedCardFinder(CHUNKS)) == [AnswerKey(
        question_id="card/neutral.tempest/cost", category="factual", question="How many Shards does Tempest cost?",
        answerable=True, citations=["neutral.tempest"], rules=[], named_cards=["neutral.tempest"], trace=[])]
