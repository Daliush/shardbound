# Evaluation questions

Every question of the Arbiter's evaluation dataset, with its answer key (design doc §6.1). An answer key never comes from a hand or a model: the engine computes the scenarios', the card data gives the factual ones', `DeckValidator` the deck questions', and the rulebook the simple-rule ones'. The wording and the dev / test split are added afterwards in Python (`ai/dataset`).

| File | Category | Where the questions come from |
|---|---|---|
| `scenarios.jsonl` | interaction, multi-step | the recipes, played by the engine |
| `cards.jsonl` | factual | `card.CardQuestions`, from the card data |
| `decks.jsonl` | deck building | `deck.DeckQuestions`, answered by `DeckValidator` |
| `rules.jsonl` | simple rule | written once, kept in `src/main/resources/questions/rules.jsonl` |
| `unanswerable.jsonl` | unanswerable | written once, kept in `src/main/resources/questions/unanswerable.jsonl` |

```bash
# from the repository: both steps, engine then wording and split
scripts/build-dataset.sh v2 --base v1
# from engine/: this module alone, into evals/datasets/v2/
./mvnw -q -pl dataset -am compile exec:java -Dexec.args=v2
```

`Dataset` builds every file at once, so it also checks that no two questions share an id, across files, and that every rule a deck question cites is in the rulebook.

## Scenarios

A **recipe** covers one interaction, such as an Anchored unit hit by Destroy. It finds the cards that fit by their data (`Cards`), builds one board per combination of cards and variants with the scenario service, and names, for each scenario, the choices to play, the player who asks, a template question, what answers it (`Answer`) and the context rules.

What applies an effect is a `Source`: a spell cast from hand, or the attack ability of a unit on the board. A recipe treats both alike, so a new card of either kind that fits joins without new code.

A question asks one of two things (`Answer`):

- **what happens**: the scenario selects the events of the outcome that answer it (`Answer.Events`), and may add facts read from the final state when no event states the value asked, such as a unit's defense once a bonus ends (`StateFacts`);
- **whether a move is legal** ("Can I play its next step this turn?"): the engine answers from the legal actions of the decision the game waits for once the choices are played (`Answer.Legality`, with `Moves`). The recipe writes the sentence for each outcome, and the rule that decides it is a context rule.

`DatasetBuilder` plays every scenario, then reads its answer key (`AnswerKeys`):

- **key facts**: the sentences of the selected events as the asker would see them in a game, redacted (another player's draw names no card) and without instance ids (`EventDescriber.withoutInstanceIds`); or the legality sentence;
- **rules**: the rule IDs the selected events carry;
- **context rules**: the rules the recipe names because they explain the situation and no event carries them, such as 11.3.6 when a unit's protection is over. With `rules`, they are the essential rules a correct answer cites;
- **trace**: every event, incidental ones included.

It stops on a mistake in a recipe: two scenarios with one id, two recipes with one interaction, a scenario whose outcome has no key event or stops on a decision nobody answered (an echo order, a target), a context rule that is not in the rulebook, or a question that names a card its asker cannot know. A card in the opponent's hand or deck is hidden, unless the opponent plays it during the scenario, it is a Fracture card already played (11.2.5), or the same card is also in plain sight.

One line of `scenarios.jsonl`:

```json
{"id":"anchor+destroy/opponents/protected/root.root-sentinel/neutral.crystal-rupture","category":"interaction","interaction":"anchor+destroy","question":"My opponent played Root Sentinel on their last turn. If I cast Crystal Rupture on it, what happens to it?","cards":["neutral.crystal-rupture","root.root-sentinel"],"key_facts":["Root Sentinel is anchored: it is not destroyed."],"rules":["11.3.2","11.3.3"],"context_rules":["11.3.1"],"trace":["CardPlayed[6.3]","AnchorPrevented[11.3.2, 11.3.3]","SpellResolved[6.3, 3.5]"]}
```

`interaction` decides the split; `cards` lets cards be held out between train and test.

## The recipes

25 interactions, 177 scenarios with the current cards.

| Package | Interaction | Scenarios | What it asks |
|---|---|---|---|
| `anchor` | `anchor+destroy`, `anchor+return-to-hand` | 18, 12 | a protected unit stays; once its protection is over, it leaves (11.3.2, 11.3.3, 11.3.6) |
| | `anchor+sacrifice` | 12 | a protected unit sacrificed stays, and the sacrifice counts as paid (11.3.3) |
| | `anchor+doom`, `anchor+doom-heal` | 12, 6 | lethal damage dooms it; destroyed at the end of the turn its protection ends, unless healed (11.3.4, 11.3.5) |
| | `anchor+link` | 18 | destruction is not shared, and the link breaks (11.5.3, 11.5.4) |
| `echo` | `echo+death` | 16 | the echo replays at X%, aimed at the asker's only unit or at the asker (11.1.1–3, 7.3) |
| | `echo+return-to-hand` | 6 | a unit returned to hand does not echo (11.1.5) |
| | `echo+chain` | 4 | an echo that kills an Echo unit starts a chain; echoes of units that die together follow 9.8 (11.1.6) |
| | `echo+modify` | 5 | the echo replays the printed attack, without the boost (11.1.9, 8.18) |
| | `echo+order` | 2 | two echoes replay in the order their owner chooses, which decides what the second hits (11.1.8) |
| `link` | `link+damage`, `link+no-share` | 4, 6 | damage is split, rounded up for the unit hit; nothing else is shared (11.5.2, 11.5.3) |
| `fracture` | `fracture+steps` | 14 | each step returns the card to hand until the last; two steps never in one turn (11.2.2–4) |
| | `fracture+lost-progress` | 4 | a full hand or a discard loses the progress (11.2.6, 11.2.7) |
| `overcharge` | `overcharge+locked-shards` | 4 | 2 Shards less now, 2 locked next turn; locks stack, the excess is lost (11.4.1–4) |
| | `overcharge+attack-cost` | 2 | Overcharge never lowers an attack's cost (11.4.5) |
| `general` | `death+trigger-order` | 8 | a spell resolves before the abilities it triggers, which follow arrival order; the game ends at once (9.8, 9.11, 1.6) |
| | `freeze+intercept` | 3 | a frozen unit, or one that already intercepted, cannot intercept (7.5, 8.10) |
| | `aura+damage`, `aura+end` | 4, 2 | an aura's defense keeps a unit alive; when the aura ends, only what is above the new max goes (8.14, 8.5) |
| | `modify+expiry` | 4 | a bonus ending caps the defense, a malus ending gives back what it took, a malus to 0 max destroys (8.5, 8.17, 6.10) |
| | `attack-ability` | 3 | an "Attack" ability resolves before the attack and the intercept (9.9, 7.9) |
| | `sacrifice-effect` | 6 | no units, no spell; with more, its controller chooses; an anchored unit counts but stays (8.16, 11.3.3) |
| | `recall` | 2 | Recall only takes from its controller's graveyard (8.13, 10.5) |

What the current cards cannot produce yet: Recall into a full hand (playing the spell frees a place). A recipe gets it as soon as a card makes it possible.

## Adding a recipe

1. Write it in the sub-package of its keyword (`recipe.anchor`…), as `AnchorRemovalRecipe` does: loop over the cards that fit and the variants, and give each scenario a stable id, `<interaction>/<variant>/…/<card ids>`.
2. Keep the board minimal: the scenario service's default board plus only what the interaction needs. When an attack aims at a unit whose side has another one, the defender may intercept (7.5): keep that unit alone, or play the defender's choice too.
3. Select the events that answer the question, and only those, and name the context rules no event carries. When a key fact names something the asker cannot work out, such as a turn number, the question says it.
4. Add it to `Recipes.all()`, and write its test: the number of scenarios, and the answer key of one scenario per variant.

## Factual questions

`card.CardQuestions` asks about every card but tokens, from its data: its faction, its cost (each step's, for a Fracture spell), its keywords, its sacrifice cost, a unit's defense and attack abilities, and what each attack, spell, Fracture step and ability does. What a card does is quoted as `CardTextRenderer` prints it, so the key fact matches the card text the Arbiter retrieves.

A factual question's group is its card, which is also its essential citation: the card's chunk. It has no essential rule. One line of `cards.jsonl`:

```json
{"id":"card/ember.ash-warden/attack-1","category":"factual","card":"ember.ash-warden","question":"What does Ash Warden's Cinder Bite do?","key_facts":["Ash Warden's Cinder Bite costs 2 Shards.","Ash Warden's Cinder Bite: \"Deal 4 damage to the target.\"","Ash Warden's Cinder Bite has Echo 50."]}
```

## Deck-building questions

`deck.DeckQuestions` asks "is this deck legal?" about each starter deck and about mutations of it, and the engine's `DeckValidator` answers: "The deck is legal.", or "The deck is not legal." and one key fact per problem it finds (`DeckProblem`), with that problem's rule as the essential rule. A legal deck passes every rule of section 2, so its essential rules are 2.1 to 2.4.

Each mutation breaks one rule, and is the question's group: `legal` (the deck as it is, or one card swapped for another that fits), `card-count` (a 31st card, or a 29-card deck, 2.1), `faction` (a card of another faction, 2.2), `copies` (a third copy, 2.3) and `token` (2.4, only in the deck of the token's faction, since a token of another faction would break 2.2 too). The cards a mutation moves are drawn with a fixed seed (`Dataset.DECK_SEED`). A mutation that breaks another rule than its own stops the build.

The question shows the whole deck in `decklist`, one line per card sorted by name, so no line gives the change away. The decklist is written here and never reworded: the Python wording only paraphrases `question`, then appends the decklist. One line of `decks.jsonl`:

```json
{"id":"deck/ember-starter/copies/+ember.spark-dart/-ember.ashborn-drake","category":"deck-building","mutation":"copies","question":"Can I play this deck?","decklist":"Faction: Ember\n2× Ash Warden\n1× Ashborn Drake\n…\n3× Spark Dart","cards":["ember.ash-warden","…"],"key_facts":["The deck is not legal.","The deck has 3 copies of Spark Dart, but a deck can contain at most 2 copies of a card."],"rules":["2.3"]}
```

## Questions written once

The simple-rule and unanswerable questions are not computed: they were written once, for v1, and are kept as data in `src/main/resources/questions/`. The engine writes them out with the rest of a version, after checking that their answer key still holds.

**Simple rule** (`rule.RuleQuestions`): one question per rule of sections 1 to 11, whose answer is that rule. Luna 6 wrote most of them; the ones a review found flawed were rewritten as templates, which the wording step rewords. The key facts are the rule's own sentences, examples left out; a rule of section 5 starts each fact with its subheading ("Start of turn: …"), since the heading says when the rule applies. Each line keeps the text of its rule (`rule_text`): a rule that changed since its question was written stops the build, since its key facts would be out of date. Write its question and key facts again.

**Unanswerable** (`unanswerable.UnanswerableQuestions`): invented keywords, cards and mechanics (`source: "invented"`), and questions on the points `docs/rules/12-open-points.md` still lists as open (`source: "open-point"`). The answer key is an abstention: no key fact. An invented name that a card or the rulebook now uses, or an open point now settled, stops the build: the question would have an answer.

One line of `rules.jsonl` and one of `unanswerable.jsonl`:

```json
{"id":"rule/7.5","category":"simple-rule","rule":"7.5","question":"What does it mean for a unit to intercept an attack, and what restrictions apply when it does so?","rule_text":"Intercept: when an attack targets one of their units, …","key_facts":["Intercept: when an attack targets one of their units, the defender may redirect the attack to another of their units, which becomes the new target.","A unit can intercept at most once per turn.","A frozen unit cannot intercept.","Neither a spell nor an echo can be intercepted (11.1.4)."]}
{"id":"unanswerable/keyword/siphon/1","category":"unanswerable","group":"invented/siphon","question":"What does the Siphon keyword do?","source":"invented","name":"Siphon","protected_terms":["Siphon"]}
```
