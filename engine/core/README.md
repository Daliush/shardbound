# engine/core

The rules engine of Shardbound, as a library: Java 21, Jackson only, no framework. It applies the rules of the [rulebook](../../docs/rules/README.md), lists the legal actions, and reports what happened with the ID of every rule applied. It never decides anything. Its contract is [`specs/phase-2-engine.md`](../../specs/phase-2-engine.md).

## Entry points

Each package has one job, and these are the classes to start from (everything is under `fr.daliush.shardbound.core`).

| To | Start from | Package |
|---|---|---|
| Load the cards and decks | `ContentLoader.load(contentDir)` | `content` |
| Play a game | `GameEngine`: `newGame`, `decision`, `apply`, `view`, `eventsFor` | `rules` |
| Write a player | `Player` (a view and a decision in, one of its actions out); `Bot`, a player whose only memory is its generator | `bot` |
| Play against a bot | `RandomBot`, `GreedyBot` | `bot.random`, `bot.greedy` |
| Build a whole game from one player's view | `Determinizer` | `determinization` |
| Set up a game at any moment, play it, read the rules applied | `ScenarioBuilder`, `ScenarioRunner`, `ScenarioResult` | `scenario` |
| Store and restore a game | `GameJson` | `json` |
| Write card texts, log lines and button labels | `CardTextRenderer`, `EventDescriber`, `ActionDescriber`, `DecisionDescriber` | `text` |

## The scenario service

A scenario is a game created at any moment instead of from the start, then played with choices whose outcome is read with the rules applied. Rule tests use it, and the Arbiter's answer keys come from it: the engine computes the ground truth, nobody writes it by hand.

### 1. Build a board

```java
Content content = ContentLoader.load(Path.of("content"));   // the repository's content/ folder
GameEngine engine = new GameEngine(content.catalog());

GameState start = ScenarioBuilder.of(content.catalog())
        .shards(P1, 5).hp(P1, 2).hand(P1, "ember.cinderfall")
        .unit(P2, "ember.cinderling").unit(P2, "ember.cinderling")
        .build();
```

Cards are named by id. What the builder sets:

- the game: `turn`, `active`, `firstPlayer`, `seed` (the game's generator: random targets and discards);
- each player: `hp`, `fatigue`, `shards`, `maxShards`, `lockedShards` (Overcharge), `deck(...)` (top card first), `hand(...)`, `handAtStep(player, card, nextStep)` (a Fracture card in progress, 1-based), `graveyard(...)`, `decklist(player, deck)`;
- the boards: `unit(player, card)`, or `unit(player, card, unit -> unit.defense(2).arrivedThisTurn().hasAttacked().hasIntercepted().frozenThroughTurn(4).anchorProtected().doomed())`, `link(card, otherCard)` between two units already placed, and `relic(player, card)`.

`build()` checks the zone limits and that links are mutual, and leaves no decision pending.

### 2. Play choices

```java
ScenarioResult result = new ScenarioRunner(engine).run(start, play("ember.cinderfall"));
```

The runner first runs the state to its first decision, then each choice answers the decision pending at its turn, whoever it belongs to. Choices name cards and targets, never an action's index, so a scenario does not break when the list of legal actions grows. From `Choices` and `Pick`:

- `keepHand()`, `mulligan()`, `endTurn()`;
- `play(card)`, with `.on(pick...)` (one pick per choice slot, two for Link), `.sacrificing(pick...)` for a sacrifice cost and `.overcharged()`;
- `attack(card)`, with `.withAttack(i)` and `.on(pick)`;
- the other player's answers in the middle of a turn: `declineIntercept()`, `interceptWith(card)`;
- choices asked while something resolves: `chooseTarget(pick)` (10.6), `chooseOrder(i, j)` for two echoes (11.1.8), `sacrifice(pick...)` and `discard(card...)`;
- picks: `unit(card)`, `unit(card, nth)`, `relic(card)`, `player(id)`, `graveyardCard(card)`.

An intercept, for example, is the defender's choice:

```java
ScenarioResult intercepted = new ScenarioRunner(engine).run(
        ScenarioBuilder.of(content.catalog()).shards(P1, 1).unit(P1, "ember.cinderling")
                .unit(P2, "neutral.shard-construct").unit(P2, "neutral.shardling").build(),
        attack("ember.cinderling").on(unit("neutral.shard-construct")),   // P1's main decision
        interceptWith("neutral.shardling"));                              // P2's intercept (7.5)

intercepted.trace();   // [AttackDeclared[7.4], AttackIntercepted[7.5], UnitDamaged[8.1], UnitDestroyed[6.6]]
```

### 3. Read the outcome and its trace

```java
result.state().result();   // Optional[Win[winner=P2, reason=HP]]
result.trace();
// CardPlayed[6.3]            Cinderfall is played
// UnitDamaged[8.1]           one effect hits both Cinderlings...
// UnitDamaged[8.1]
// UnitDestroyed[6.6]         ...and they die together
// UnitDestroyed[6.6]
// SpellResolved[6.3, 3.5]    the spell resolves completely before the abilities it triggered (9.11)
// AbilityTriggered[9.3]      the first Cinderling's Death ability
// PlayerDamaged[8.1]         P1 drops to 0 HP
// GameEnded[1.2, 1.6]        at once: the second Death ability never resolves
```

`ScenarioResult` holds:

| | |
|---|---|
| `state()` | the final `GameState`, hidden cards included |
| `events()` | every event, **unredacted**, in the order the rules applied; `events(GameEvent.UnitDamaged.class)` keeps one type |
| `trace()` | the same, one `"Type[rule, rule]"` line per event: one list checks the order of the steps and the rules cited |
| `decisions()` | the decision each choice answered, in order |
| `pending()` | the decision the game waits for at the end, empty once it is over |
| `unit(card)`, `findUnit(card)`, `player(id)` | the first unit with that card, oldest arrival first; a player's side |

### From a scenario to an answer key

```java
List<String> citedRules = result.events().stream().flatMap(e -> e.rules().stream()).distinct().toList();
```

The order of the events is the order the rules applied, and each event names its rule IDs: the Arbiter's citations and step order are graded against them (design doc §6.3).

### Defaults

- Turn 3, P1 active and first player, so each player has taken their turns (P1 2, P2 1).
- Both players at 50 HP with 0 Shards; empty hands, decks and graveyards; mulligans decided; seed 1.
- Units at full defense, arrived on an earlier turn, so they can attack.
- Instance ids and arrival order follow the order of the calls: the first card named is `#1`, the first unit placed arrives first.

### Pitfalls

- **0 Shards by default.** "0 actions match" in an error means the choice found nothing to do: too few Shards, a unit that arrived this turn or is frozen, a target that is not valid. The message lists the decision's actions: `0 actions match "attack with ember.cinderling" in MAIN decision d-1: [EndTurn[]]`.
- **Several matches.** A choice must name exactly one action: add the target (`.on(...)`), the attack (`.withAttack(1)`) or the sacrifices.
- **Empty decks.** A draw from an empty deck costs fatigue HP (1.4): when a turn passes, give the next player a `deck(...)`.
- **One choice per decision.** Every decision met needs its choice, whoever it belongs to: the other player's intercept, a target an ability asks when it starts resolving, the order of two echoes. A choice with a single option is never asked: it is taken automatically.
- **The state is checked first.** As soon as the scenario runs, a unit at 0 defense dies (unless Anchor keeps it, doomed: 11.3.4) and a player at 0 HP loses.
- **Events are unredacted.** They name every card, the hidden ones included: an answer key, never something to send to a player. `GameEngine.eventsFor(events, viewer)` gives what a player may see.

## Bots and determinization

A bot sees what a player sees, its `PlayerView`, and answers with one of the decision's actions. Its only memory is its generator, so a server rebuilds it at every move:

```java
Bot bot = new GreedyBot(engine, rngState);
Action action = bot.choose(engine.view(state, seat, history), decision);
long next = bot.rngState();   // all the server keeps until the next move
```

`GreedyBot` looks one action ahead without cheating: it never touches the real game, but a game the `Determinizer` builds from its view alone. The public information is copied, its own deck is shuffled, the opponent's cards that went back to their hand in public stay as they are, and the rest of the opponent's cards are drawn among the copies their faction and the neutral cards have left:

```java
GameState world = new Determinizer(content.catalog()).determinize(view, seed);   // paused on the viewer's decision
```

## Tests

From `engine/`:

```bash
./mvnw verify                    # every test, the 60% target of GreedyBot included
./mvnw -pl core test -Preports   # on demand, a few minutes: greedy against random, and the decks' balance
```

The reports print Markdown tables and write them to `core/target/reports/`.
