# Engine cookbook

Code for each consumer of `engine/core`. Imports are omitted; everything is under `fr.daliush.shardbound.core`.

## Contents

1. Load the content
2. Play a game between two players
3. Write a bot
4. What a game server sends to each seat
5. Readable logs and button labels
6. A rule test with the scenario service
7. An answer key: the exact outcome and the rules applied
8. Store and restore a game
9. Run the tests

## 1. Load the content

```java
Content content = ContentLoader.load(Path.of("content"));   // the repository's content/ folder
CardCatalog catalog = content.catalog();
Deck ember = content.deck("ember-starter");
List<String> problems = new DeckValidator(catalog).problems(ember);   // empty when legal (section 2)
```

In tests, use `testing.TestContent.content()`. It finds `content/` by walking up from the working directory, and caches it. `testing.TestCards.CATALOG` is the real catalog plus a few `test.*` cards, for rules that no real card exercises yet.

The loader is strict. An unknown field, an unknown enum value, a missing field, an `attack_target` outside an attack, a deck card that does not exist, or a summon of a non-token all throw `ContentException`, naming the file and the field.

## 2. Play a game between two players

```java
GameEngine engine = new GameEngine(catalog);
Map<PlayerId, Player> seats = Map.of(PlayerId.P1, new RandomBot(1), PlayerId.P2, new RandomBot(2));

Transition t = engine.newGame(new GameSetup(ember, root, 42L));
List<GameEvent> history = new ArrayList<>(t.events());
GameState state = t.state();
for (Optional<Decision> d = engine.decision(state); d.isPresent(); d = engine.decision(state)) {
    PlayerId decider = d.get().player();
    Action action = seats.get(decider).choose(engine.view(state, decider, history), d.get());
    t = engine.apply(state, action);
    history.addAll(t.events());
    state = t.state();
}
```

The test utility `testing.GameDriver.play(engine, setup, p1, p2, (state, decision) -> …)` does exactly this, and lets you check every state reached. That is how `RandomGamesTest` checks the invariants after every step.

Ask the decision for the player, not `state.active()`: intercepts and some choices belong to the non-active player.

## 3. Write a bot

```java
public final class FirstOptionBot implements Player {
    @Override
    public Action choose(PlayerView view, Decision decision) {
        return decision.actions().getFirst();     // always one of the listed actions
    }
}
```

- A bot sees only its `PlayerView`: its own hand, public zones, the opponent's hand count, and the redacted history. Never hand it a `GameState`. That would be cheating, and the evaluation depends on bots playing fair (design doc §6.5).
- If a bot needs randomness, give it its own `SplitMix64(seed)`, as `RandomBot` does. The game's RNG is part of the state and must not move because of a bot.
- To look ahead, a bot applies actions to a state built from its view (determinization, slice 5), never to the real state.

## 4. What a game server sends to each seat

After each `apply`, for each seat:

```java
PlayerView view = engine.view(newState, seat, fullHistory);           // the state to render
List<GameEvent> news = engine.eventsFor(transition.events(), seat);   // this action's events, redacted
```

The full history and the `GameState` stay on the server. A reconnecting client gets `view` again, with the whole redacted history (spec §13). `engine/api` does exactly this; the `shardbound-game-server` skill explains how.

## 5. Readable logs and button labels

```java
EventDescriber events = new EventDescriber(catalog);
for (GameEvent event : engine.eventsFor(history, viewer)) {
    System.out.println(event.rules() + " " + events.describe(event, viewer));
}
// [5.2] Turn 4: your turn.
// [8.1] Sprout #61 takes 3 damage.

ActionDescriber actions = new ActionDescriber(catalog);
String label = actions.describe(action, state, decision.player());   // "Play Spark Dart (1 Shard) on Sprout #3"
```

A card's rules text, line by line, comes from the card data (spec §9):

```java
CardText text = new CardTextRenderer(catalog, content.textTemplates()).render(catalog.card(new CardId("ember.ash-warden")));
text.texts();   // ["Cinder Bite (2 Shards): Deal 4 damage to the target. Echo 50.", "Kindle (1 Shard): Give all your units +2/+0 until end of turn."]
text.lines();   // the same, each with its kind: ATTACK, ATTACK
```

Always describe events after redaction. `ActionDescriber` reads the real state to name cards, so it runs on the server, and only for the deciding player's own actions. `DecisionDescriber.describe(decision, state)` writes the decision's prompt under the same condition, and `rules.combat.AttackDamage.toTarget(attack, unit)` gives the damage an attack shows (bonuses included, empty when it deals none).

Printing a described log of a seeded game is the fastest way to see what the engine did.

## 6. A rule test with the scenario service

```java
@Test
@DisplayName("7.5 — the defender may redirect the attack to another of their units")
void intercept() {
    ScenarioResult result = run(scenario().shards(P1, 1).unit(P1, "ember.cinderling")
                    .unit(P2, "neutral.shard-construct").unit(P2, "neutral.shardling").build(),
            attack("ember.cinderling").on(unit("neutral.shard-construct")),
            interceptWith("neutral.shardling"));

    assertThat(trace(result)).containsSubsequence("AttackIntercepted[7.5]", "UnitDamaged[8.1]");
    assertThat(result.unit("neutral.shard-construct").defense()).isEqualTo(9);
}
```

These come from static imports of `testing.RuleTesting` (`scenario()`, `run(…)`, `trace(…)`, `ENGINE`), `scenario.Choices` and `scenario.Pick`. `trace` turns the events into `"Type[rule, rule]"` lines, so one assertion checks both the order and the rule IDs.

Builder reference:

- `turn`, `active`, `firstPlayer`, `seed`;
- per player: `hp`, `fatigue`, `shards`, `maxShards`, `lockedShards` (Overcharge), `deck(…)` (top first), `hand(…)`, `handAtStep(player, card, nextStep)` (a Fracture card in progress, 1-based), `graveyard(…)`;
- `unit(player, card, setup -> setup.defense(2).arrivedThisTurn().hasAttacked().hasIntercepted().frozenThroughTurn(4).anchorProtected().doomed())`;
- `link(card, otherCard)`: links two units already placed (the builder checks links are mutual);
- `relic(player, card)`;
- `decklist(player, deck)`.

Choices:

- `keepHand()`, `mulligan()`, `endTurn()`, `declineIntercept()`;
- `interceptWith(card)`, `chooseTarget(pick)`;
- `play(card).on(pick…)`, with one pick per choice slot (two for Link), `.sacrificing(pick…)` for a sacrifice cost and `.overcharged()` (11.4.1);
- `chooseOrder(i, j)` for the order of two echoes (11.1.8);
- `sacrifice(pick…)` and `discard(card…)` to answer a `CHOOSE_CARDS` decision;
- `attack(card).withAttack(i).on(pick)`.

Picks: `unit(card)`, `unit(card, nth)`, `relic(card)`, `player(id)`, `graveyardCard(card)` (Recall).

## 7. An answer key: the exact outcome and the rules applied

```java
ScenarioResult result = new ScenarioRunner(engine).run(start, choices…);
GameState outcome = result.state();
List<String> citedRules = result.events().stream().flatMap(e -> e.rules().stream()).distinct().toList();
```

The events are unredacted, and their order is the order the rules applied. This is what the Arbiter's citations and step order are graded against (design doc §6.3).

## 8. Store and restore a game

```java
GameJson json = new GameJson();
String saved = json.write(state);             // every field, hidden cards included: server side only
GameState restored = json.readState(saved);   // equal to state, and plays on identically
String log = json.writeEvents(history);
List<GameEvent> back = json.readEvents(log);
```

A paused game, waiting for an intercept or a target, survives this too: the pending decision and the paused step are in the state.

## 9. Run the tests

```bash
cd engine && ./mvnw verify                                        # everything
cd engine && ./mvnw -pl core test -Dtest=CombatRulesTest          # one class
cd engine && ./mvnw -pl core test -Dtest='CombatRulesTest#intercept'
```
