# Evaluation scenarios

The Arbiter's interaction questions and their answer keys, computed by the engine instead of written by hand (design doc §6.1). This module builds the scenarios and exports them; the questions' final wording and the train / dev / test split are added later, in Python.

```bash
# from engine/
./mvnw -q -pl dataset -am compile exec:java -Dexec.args=v1   # writes evals/datasets/v1/scenarios.jsonl
```

## How it works

A **recipe** covers one interaction, such as an Anchored unit hit by Destroy. It finds the cards that fit by their data (`Cards`), builds one board per combination of cards and variants with the scenario service, names the choices to play, the player who asks, a template question and the context rules, and says which events answer the question (`Recipe.isKey`).

`DatasetBuilder` plays every scenario, then turns the events the recipe selected into the answer key (`AnswerKeys`):

- **key facts**: their sentences, from the asker's point of view and without instance ids (`EventDescriber.withoutInstanceIds`);
- **rules**: the rule IDs they carry;
- **context rules**: the rules the recipe names because they explain the situation and no event carries them, such as 11.3.6 when a unit's protection is over. With `rules`, they are the essential rules a correct answer cites;
- **trace**: every event, incidental ones included.

It stops on a mistake in a recipe: two scenarios with one id, a scenario whose outcome has no key event, a context rule that is not in the rulebook, or a question that names a card hidden from its asker.

One line of `scenarios.jsonl`:

```json
{"id":"anchor+destroy/opponents/protected/root.root-sentinel/neutral.crystal-rupture","category":"interaction","interaction":"anchor+destroy","question":"My opponent played Root Sentinel on their last turn. If I cast Crystal Rupture on it, what happens to it?","cards":["neutral.crystal-rupture","root.root-sentinel"],"key_facts":["Root Sentinel is anchored: it is not destroyed."],"rules":["11.3.2","11.3.3"],"context_rules":["11.3.1"],"trace":["CardPlayed[6.3]","AnchorPrevented[11.3.2, 11.3.3]","SpellResolved[6.3, 3.5]"]}
```

`interaction` decides the split; `cards` lets cards be held out between train and test.

## Adding a recipe

1. Write it in the sub-package of its keyword (`recipe.anchor`…), as `AnchorDestroyRecipe` does: loop over the cards that fit and the variants, and give each scenario a stable id, `<interaction>/<variant>/…/<card ids>`.
2. Keep the board minimal: the scenario service's default board plus only what the interaction needs.
3. Select the events that answer the question, and only those, and name the context rules no event carries.
4. Add it to `Recipes.all()`, and write its test: the number of scenarios, and the answer key of one scenario per variant.
