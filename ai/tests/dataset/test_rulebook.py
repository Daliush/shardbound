import re


def test_reads_every_rule_of_the_rulebook_once(rules, rulebook_text):
    written = re.findall(r"\*\*(\d+\.\d+(?:\.\d+)?)\*\*", rulebook_text)

    assert sorted(rule.id for rule in rules) == sorted(set(written))
    assert all(rule.facts for rule in rules)


def test_keeps_a_rule_s_section_and_its_own_sentences(rules):
    intercept = _rule(rules, "7.5")
    anchor = _rule(rules, "11.3.2")

    assert (intercept.section, intercept.title) == ("7", "Combat")
    assert intercept.facts == (
        "Intercept: when an attack targets one of their units, the defender may redirect the attack to another of "
        "their units, which becomes the new target.",
        "A unit can intercept at most once per turn.",
        "A frozen unit cannot intercept.",
        "Neither a spell nor an echo can be intercepted (11.1.4).",
    )
    assert (anchor.section, anchor.title) == ("11.3", "Anchor")


def test_gives_each_item_the_words_that_introduce_it(rules):
    assert _rule(rules, "7.2").facts[1] == "A unit can attack if all of the following hold: it is not frozen."
    assert _rule(rules, "6.2").facts[0] == "There are three card types."


def test_leaves_out_examples_and_titles(rules):
    assert not any("Example" in fact for fact in _rule(rules, "8.5").facts)
    assert _rule(rules, "1.4").facts[0].startswith("When a player must draw from an empty deck")


def test_keeps_a_quoted_card_text_in_one_sentence(rules):
    assert ('Examples: "Deal 4 damage to the target. Draw 1 card." or "Give all your units +5/+0 until end of turn."'
            in _rule(rules, "7.8").facts)


def _rule(rules, rule_id):
    return next(rule for rule in rules if rule.id == rule_id)
