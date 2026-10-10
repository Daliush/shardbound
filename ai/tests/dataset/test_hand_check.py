from collections import Counter

from dataset import hand_check


def _entries():
    sizes = {"factual": 214, "interaction": 155, "simple-rule": 130, "unanswerable": 98, "deck-building": 49,
             "multi-step": 22}
    return [{"id": f"{category}/{index}", "category": category, "group": f"{category}/{index % 7}", "split": "test",
             "question": "?", "template_question": "?", "answerable": True, "key_facts": [], "citations": []}
            for category, count in sizes.items() for index in range(count)]


def test_samples_150_questions_in_proportion_with_at_least_10_per_category():
    sample = hand_check.sample(_entries(), 150, 1)
    per_category = Counter(row["category"] for row in sample)

    assert len(sample) == 150
    assert len({row["id"] for row in sample}) == 150
    assert min(per_category.values()) >= 10
    assert per_category["factual"] > per_category["interaction"] > per_category["deck-building"]


def test_spreads_a_category_s_sample_over_its_groups():
    sample = hand_check.sample(_entries(), 150, 1)
    groups = {f"multi-step/{int(row['id'].split('/')[1]) % 7}" for row in sample if row["category"] == "multi-step"}

    assert len(groups) == 7


def test_leaves_a_review_for_the_reader_to_fill():
    row = hand_check.sample(_entries(), 150, 1)[0]

    assert row["review"] == {"question": None, "answer_key": None, "notes": ""}
