"""text-templates.json has wording for everything the card schema allows.

If someone adds an effect, target, trigger or keyword to the schema without its template,
the card text generator would have nothing to print: these tests catch it.
"""


def effect_kinds(schema):
    kinds = {variant["properties"]["effect"]["const"] for variant in schema["$defs"]["effect"]["oneOf"]}
    kinds |= {variant["properties"]["effect"]["const"] for variant in schema["$defs"]["auraEffect"]["oneOf"]}
    return kinds


def targets(schema):
    defs = schema["$defs"]
    return {t for name in ("targetSingleUnit", "targetAllUnits", "targetPlayer", "targetRelic") for t in defs[name]["enum"]}


def triggers(schema):
    defs = schema["$defs"]
    found = set()
    for ability in ("unitAbility", "relicAbility"):
        found |= set(defs[ability]["oneOf"][0]["properties"]["trigger"]["enum"])
    return found  # "continuous" abilities are printed without a trigger label


def keywords(schema):
    defs = schema["$defs"]
    found = set(defs["unitFields"]["properties"]["keywords"]["items"]["enum"])
    for fields in ("spellFields", "relicFields"):
        found.add(defs[fields]["properties"]["keywords"]["items"]["const"])
    return found


def test_every_effect_has_a_template(schema, templates):
    assert effect_kinds(schema) <= set(templates["effects"])


def test_every_target_has_a_phrase(schema, templates):
    assert targets(schema) <= set(templates["targets"])


def test_every_trigger_has_a_label(schema, templates):
    assert triggers(schema) <= set(templates["triggers"])


def test_every_keyword_has_a_template(schema, templates):
    assert keywords(schema) <= set(templates["keywords"])


def test_every_cost_aura_card_type_has_a_word(schema, templates):
    cost_aura = next(v for v in schema["$defs"]["auraEffect"]["oneOf"] if v["properties"]["kind"]["const"] == "cost")
    assert set(cost_aura["properties"]["card_type"]["enum"]) <= set(templates["aura_cards"])
