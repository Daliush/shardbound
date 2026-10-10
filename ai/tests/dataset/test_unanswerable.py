from dataset import unanswerable


def test_invents_names_the_arbiter_s_sources_never_mention(sources, rulebook_text):
    names = [protected for question in unanswerable.questions() if question.source == "invented"
             for protected in question.protected]

    found = {name: unanswerable.present(name, sources, rulebook_text) for name in names}

    assert found == {name: [] for name in names}


def test_the_absence_check_finds_a_real_card_or_keyword(sources, rulebook_text):
    assert "Root Sentinel" in unanswerable.present("Root Sentinel", sources, rulebook_text)
    assert "Overcharge" in unanswerable.present("Overcharge", sources, rulebook_text)
    assert unanswerable.present("fatigue", sources, rulebook_text) == ["the rulebook"]


def test_asks_only_about_open_points_the_rulebook_still_lists_as_open(root):
    open_points = (root / "docs" / "rules" / "12-open-points.md").read_text(encoding="utf-8")

    assert unanswerable.still_open(open_points) == list(unanswerable.OPEN_POINTS)


def test_gives_about_100_questions_grouped_by_name_or_open_point():
    questions = unanswerable.questions()
    open_point_groups = {question.group for question in questions if question.source == "open-point"}

    assert 90 <= len(questions) <= 110
    assert len({question.id for question in questions}) == len(questions)
    assert open_point_groups == {"open-point/after-attack", "open-point/unit-size"}
    assert {question.group for question in questions if "siphon" in question.id} == {"invented/siphon"}
