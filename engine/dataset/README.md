# Evaluation scenarios

The Arbiter's interaction questions and their answer keys, computed by the engine instead of written by hand (design doc §6.1). This module builds the scenarios and exports them; the questions' final wording and the train / dev / test split are added later, in Python.

```bash
# from engine/
./mvnw -q -pl dataset -am compile exec:java -Dexec.args=v1   # writes evals/datasets/v1/scenarios.jsonl
```

## How it works

A **recipe** covers one interaction, such as an Anchored unit hit by Destroy. It finds the cards that fit by their data (`Cards`), builds one board per combination of cards and variants with the scenario service, and names, for each scenario, the choices to play, the player who asks, a template question, what answers it (`Answer`) and the context rules.

What applies an effect is a `Source`: a spell cast from hand, or the attack ability of a unit on the board. A recipe treats both alike, so a new card of either kind that fits joins without new code.

A question asks one of two things (`Answer`):

- **what happens**: the scenario selects the events of the outcome that answer it (`Answer.Events`);
- **whether a move is legal** ("Can I play its next step this turn?"): the engine answers from the legal actions of the decision the game waits for once the choices are played (`Answer.Legality`, with `Moves`). The recipe writes the sentence for each outcome, and the rule that decides it is a context rule.

`DatasetBuilder` plays every scenario, then reads its answer key (`AnswerKeys`):

- **key facts**: the sentences of the selected events as the asker would see them in a game, redacted (another player's draw names no card) and without instance ids (`EventDescriber.withoutInstanceIds`); or the legality sentence;
- **rules**: the rule IDs the selected events carry;
- **context rules**: the rules the recipe names because they explain the situation and no event carries them, such as 11.3.6 when a unit's protection is over. With `rules`, they are the essential rules a correct answer cites;
- **trace**: every event, incidental ones included.

It stops on a mistake in a recipe: two scenarios with one id, two recipes with one interaction, a scenario whose outcome has no key event, a context rule that is not in the rulebook, or a question that names a card its asker cannot know. A card in the opponent's hand or deck is hidden, unless the opponent plays it during the scenario, it is a Fracture card already played (11.2.5), or the same card is also in plain sight.

One line of `scenarios.jsonl`:

```json
{"id":"anchor+destroy/opponents/protected/root.root-sentinel/neutral.crystal-rupture","category":"interaction","interaction":"anchor+destroy","question":"My opponent played Root Sentinel on their last turn. If I cast Crystal Rupture on it, what happens to it?","cards":["neutral.crystal-rupture","root.root-sentinel"],"key_facts":["Root Sentinel is anchored: it is not destroyed."],"rules":["11.3.2","11.3.3"],"context_rules":["11.3.1"],"trace":["CardPlayed[6.3]","AnchorPrevented[11.3.2, 11.3.3]","SpellResolved[6.3, 3.5]"]}
```

`interaction` decides the split; `cards` lets cards be held out between train and test.

## The recipes

20 interactions, 127 scenarios with the current cards.

| Package | Interaction | Scenarios | What it asks |
|---|---|---|---|
| `anchor` | `anchor+destroy`, `anchor+return-to-hand` | 12, 8 | a protected unit stays; once its protection is over, it leaves (11.3.2, 11.3.3, 11.3.6) |
| | `anchor+sacrifice` | 8 | a protected unit sacrificed stays, and the sacrifice counts as paid (11.3.3) |
| | `anchor+doom`, `anchor+doom-heal` | 8, 4 | lethal damage dooms it; destroyed at the end of the turn its protection ends, unless healed (11.3.4, 11.3.5) |
| | `anchor+link` | 12 | destruction is not shared, and the link breaks (11.5.3, 11.5.4) |
| `echo` | `echo+death` | 16 | the echo replays at X%, aimed at the asker's only unit or at the asker (11.1.1–3, 7.3) |
| | `echo+return-to-hand` | 4 | a unit returned to hand does not echo (11.1.5) |
| | `echo+chain` | 4 | an echo that kills an Echo unit starts a chain; echoes of units that die together follow 9.8 (11.1.6) |
| | `echo+modify` | 5 | the echo replays the printed attack, without the boost (11.1.9, 8.18) |
| `link` | `link+damage`, `link+no-share` | 4, 6 | damage is split, rounded up for the unit hit; nothing else is shared (11.5.2, 11.5.3) |
| `fracture` | `fracture+steps` | 10 | each step returns the card to hand until the last; two steps never in one turn (11.2.2–4) |
| | `fracture+lost-progress` | 3 | a full hand or a discard loses the progress (11.2.6, 11.2.7) |
| `overcharge` | `overcharge+locked-shards` | 4 | 2 Shards less now, 2 locked next turn; locks stack, the excess is lost (11.4.1–4) |
| | `overcharge+attack-cost` | 2 | Overcharge never lowers an attack's cost (11.4.5) |
| `general` | `death+trigger-order` | 8 | a spell resolves before the abilities it triggers, which follow arrival order; the game ends at once (9.8, 9.11, 1.6) |
| | `freeze+intercept` | 3 | a frozen unit, or one that already intercepted, cannot intercept (7.5, 8.10) |
| | `aura+damage` | 4 | an aura's defense keeps a unit alive (8.14) |
| | `recall` | 2 | Recall only takes from its controller's graveyard (8.13, 10.5) |

What the current cards cannot produce yet: the end of an aura (no card removes a relic), the end of a temporary defense bonus (no card gives one), Recall into a full hand (playing the spell frees a place), an "Attack" ability (9.9), and a unit with two Echo attacks (11.1.8). A recipe gets these as soon as a card makes them possible.

## Adding a recipe

1. Write it in the sub-package of its keyword (`recipe.anchor`…), as `AnchorRemovalRecipe` does: loop over the cards that fit and the variants, and give each scenario a stable id, `<interaction>/<variant>/…/<card ids>`.
2. Keep the board minimal: the scenario service's default board plus only what the interaction needs. When an attack aims at a unit whose side has another one, the defender may intercept (7.5): keep that unit alone, or play the defender's choice too.
3. Select the events that answer the question, and only those, and name the context rules no event carries. When a key fact names something the asker cannot work out, such as a turn number, the question says it.
4. Add it to `Recipes.all()`, and write its test: the number of scenarios, and the answer key of one scenario per variant.
