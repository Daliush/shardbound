# Phase 2 — What the engine and the API return

> **Status**: hand-written examples, reviewed with the maintainer on 2026-10-05. The contract is [`phase-2-engine.md`](phase-2-engine.md) (sections 5, 6, 12, 13); this document shows it on a real situation. The choices it made beyond the spec are listed in [Decisions taken on these examples](#decisions-taken-on-these-examples). The real engine output may differ in wording.

## The situation used everywhere

You created a game with **Ember Starter** against the `random` bot playing **Root Starter**. You sit in seat P1, the bot in P2, and the RNG made you play first.

| Turn | Who | What happened |
|---|---|---|
| setup | both | 5 cards each, both keep their hand |
| 1 | you | Cinderling #1 |
| 2 | bot | Sproutcaller #31, whose Arrival ability summons a Sprout token #61 |
| 3 | you | Shardling #7; Cinderling #1 attacks Sproutcaller #31 with Flick; the bot does not intercept; Sproutcaller dies |
| 4 | bot | Root Sentinel #36 (Anchor) |
| 5 | you | you just drew Ashborn Drake: **it is your main phase** |

**Instance ids** (#1, #61…) name one physical copy of a card in this game: your 30 cards are #1–#30 and the bot's #31–#60, in decklist order, and every token created afterwards, by either player, takes the next number (#61, #62…). Your Spark Darts are #3 and #4 because Spark Dart is the second entry of `ember-starter.json`. Why the engine needs them: see [Decisions](#decisions-taken-on-these-examples), point 1.

Twelve actions have been applied so far (2 mulligan decisions, then plays, the attack, the intercept answer and the turn ends), so the game is at **version 12**, and the decision you face is the 13th one: **`d-13`**.

---

## 1. Engine (`engine/core`, Java)

### 1.1 Starting a game and reading the decision

```java
Content content = ContentLoader.load(contentDir);
GameEngine engine = new GameEngine(content.catalog());

Transition t = engine.newGame(new GameSetup(
        content.decks().get(new DeckId("ember-starter")),
        content.decks().get(new DeckId("root-starter")),
        42L));

Decision d = engine.decision(t.state()).orElseThrow();
// Decision[id=d-1, player=P1, kind=MULLIGAN, actions=[KeepHand[], Mulligan[]]]

t = engine.apply(t.state(), d.actions().get(0));   // keep the hand; returns the new state + its events
```

A **`Transition`** is what `newGame` and `apply` return: the new state, and the events that describe how the game got there (`record Transition(GameState state, List<GameEvent> events)`). The engine never modifies a state; it returns a new one, so it has to return what happened alongside it. `GameState` is immutable: a bot or MCTS can keep the old state and try another action from it.

### 1.2 The turn 5 decision

`engine.decision(state)` at turn 5. Actions are listed in the canonical order of spec §6.2: cards in hand order, then attacks by attacker arrival order, then `EndTurn`. The client answers with the index.

```text
Decision[id=d-13, player=P1, kind=MAIN, actions=[
   0  PlayCard(card=#3  Spark Dart,     overcharge=false, targets=[Unit #61], sacrificed=[])
   1  PlayCard(card=#3  Spark Dart,     overcharge=false, targets=[Unit #36], sacrificed=[])
   2  PlayCard(card=#19 Ember Lance,    overcharge=true,  targets=[],         sacrificed=[])
   3  PlayCard(card=#15 Ash Warden,     overcharge=false, targets=[],         sacrificed=[])
   4  PlayCard(card=#5  Pyre Offering,  overcharge=false, targets=[Unit #61], sacrificed=[#1])
   5  PlayCard(card=#5  Pyre Offering,  overcharge=false, targets=[Unit #61], sacrificed=[#7])
   6  PlayCard(card=#5  Pyre Offering,  overcharge=false, targets=[Unit #36], sacrificed=[#1])
   7  PlayCard(card=#5  Pyre Offering,  overcharge=false, targets=[Unit #36], sacrificed=[#7])
   8  Attack(attacker=#1 Cinderling, attackIndex=0, target=Unit #61)
   9  Attack(attacker=#1 Cinderling, attackIndex=0, target=Unit #36)
  10  Attack(attacker=#7 Shardling,  attackIndex=0, target=Unit #61)
  11  Attack(attacker=#7 Shardling,  attackIndex=0, target=Unit #36)
  12  EndTurn()
]]
```

Why some cards are missing or appear once:

- **Ember Lance** costs 4 and you have 3 Shards: only the overcharged version (2 Shards) is legal. Its target, `opponent`, is not a choice, so `targets` is empty.
- **Ashborn Drake** costs 6: not listed.
- **Pyre Offering** lists every (target, sacrificed unit) pair. *In slice 1 it is not listed at all: sacrifice comes in slice 3, and until then cards using it are not playable.*

(The names after the ids are for this document; the real `toString` only has ids.)

### 1.3 Applying an action: state and events

```java
Transition next = engine.apply(state, decision.actions().get(0));   // Spark Dart on Sprout #61
```

`next.events()`, unredacted, each with its rule IDs:

| # | Event | Rules |
|---|---|---|
| 1 | `CardPlayed(player=P1, card=#3 ember.spark-dart, cost=1, overcharged=false, fractureStep=empty)` | 6.3 |
| 2 | `DamageDealt(target=Unit #61, amount=3)` | 8.1 |
| 3 | `UnitDestroyed(unit=#61 root.sprout, cause=ZERO_DEFENSE)` | 6.6 |
| 4 | `TokenVanished(unit=#61 root.sprout)` | 3.6 |
| 5 | `SpellResolved(card=#3 ember.spark-dart)` | 6.3, 3.5 |

`next.state()` is the new state; `engine.decision(next.state())` is your next `MAIN` decision (`d-14`).

### 1.4 Hidden information

```java
GameEvent drawn = new CardDrawn(P2, Optional.of(new CardInstance(new InstanceId(44), new CardId("root.bramble-warden"), P2)));
drawn.visibility();   // PrivateTo[P2]
drawn.redacted();     // CardDrawn[player=P2, card=Optional.empty]: no card, no instance id

engine.eventsFor(events, P1);           // the same list, with P2's private events redacted
engine.view(state, P1, history);        // PlayerView: P2's hand is only a count, no deck order anywhere
```

### 1.5 A rule test with the scenario service

The intended style of the tests (spec §11, §16). Names and helpers may still change.

```java
@Test
@DisplayName("11.3.4 — an anchored unit dropped to 0 defense is doomed, not destroyed")
void anchoredUnitAtZeroIsDoomed() {
    GameState start = ScenarioBuilder.of(catalog)
            .turn(5).active(P1)
            .player(P1, p -> p.shards(3).hand("ember.spark-dart"))
            .player(P2, p -> p.unit("root.root-sentinel", u -> u.defense(3).anchorProtected()))
            .build();

    ScenarioResult result = ScenarioRunner.run(start,
            play("ember.spark-dart").targeting(unit("root.root-sentinel")));

    Unit sentinel = result.unit("root.root-sentinel");
    assertThat(sentinel.defense()).isZero();
    assertThat(sentinel.doomed()).isTrue();
    assertThat(result.events()).anySatisfy(e ->
            assertThat(e).isInstanceOf(UnitDoomed.class).extracting(GameEvent::rules).isEqualTo(List.of("11.3.4")));
}
```

---

## 2. REST (`/api`)

### 2.1 `GET /api/cards`

One entry per card, tokens included. `text` is generated by `CardTextRenderer` (spec §9) from the card data.

```json
[
  {
    "id": "ember.ash-warden",
    "name": "Ash Warden",
    "faction": "ember",
    "type": "unit",
    "cost": 3,
    "defense": 6,
    "token": false,
    "keywords": [],
    "text": [
      "Cinder Bite (2 Shards): Deal 4 damage to the target. Echo 50.",
      "Kindle (1 Shard): Give all your units +2/+0 until end of turn."
    ],
    "flavor": "The ashes remember."
  },
  {
    "id": "root.sprout",
    "name": "Sprout",
    "faction": "root",
    "type": "unit",
    "defense": 2,
    "token": true,
    "keywords": [],
    "text": ["Prick (1 Shard): Deal 2 damage to the target."]
  },
  {
    "id": "tide.moonpull",
    "name": "Moonpull",
    "faction": "tide",
    "type": "spell",
    "token": false,
    "keywords": [],
    "text": [
      "Fracture 3.",
      "Step 1 (1 Shard): Give an allied unit +2/+2.",
      "Step 2 (2 Shards): Give an enemy unit -2/-3.",
      "Step 3 (3 Shards): Freeze all enemy units."
    ],
    "fracture": [
      { "step": 1, "cost": 1 },
      { "step": 2, "cost": 2 },
      { "step": 3, "cost": 3 }
    ]
  }
]
```

### 2.2 `GET /api/decks` and `GET /api/bots`

```json
[
  {
    "id": "ember-starter",
    "name": "Ember Starter",
    "description": "Front-loaded aggression: cheap attackers and burn early, sacrifice and Echo in the mid game, Cinderfall against swarms.",
    "faction": "ember",
    "cards": [
      { "card": "ember.cinderling", "count": 2 },
      { "card": "ember.spark-dart", "count": 2 }
    ]
  }
]
```

(Cut to two entries here; the real response lists the 15 entries of each deck.)

```json
["random"]
```

`"greedy"` joins the list in slice 5.

### 2.3 Creating and joining a game

Against a bot:

```http
POST /api/games
{ "deck": "ember-starter", "opponent": { "type": "bot", "bot": "random", "deck": "root-starter" } }
```

```http
201 Created
{
  "gameId": "8f2c6d1e-3b7a-4c59-9e10-5a4b2d7f9c31",
  "playerToken": "kq3V9xYp0dLmR2tB7wZc4nHf8sJe1uGa6oQiTlXy5vM",
  "joinCode": null,
  "websocketPath": "/ws/games/8f2c6d1e-3b7a-4c59-9e10-5a4b2d7f9c31"
}
```

Against a human: the same request with `"opponent": { "type": "human" }` returns a `joinCode`. The second player opens the invite link `/join/8f2c…?code=…`, and their client calls:

```http
POST /api/games/8f2c6d1e-3b7a-4c59-9e10-5a4b2d7f9c31/join
{ "joinCode": "Zr5pQ0uY7wX2…", "deck": "root-starter" }
```

```http
200 OK
{ "gameId": "8f2c6d1e-…", "playerToken": "Hn2Lw…", "websocketPath": "/ws/games/8f2c6d1e-…" }
```

The seed, when given in the request, is never returned while the game runs.

### 2.4 Errors (RFC 9457 Problem Details)

```http
400 Bad Request
Content-Type: application/problem+json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Unknown deck: emberr-starter",
  "instance": "/api/games"
}
```

```http
409 Conflict
{ "type": "about:blank", "title": "Conflict", "status": 409, "detail": "Wrong join code, or the game is already full.", "instance": "/api/games/8f2c…/join" }
```

An illegal deck (not possible yet with the repository's decks) gets the messages of the Python content tests, rule ID included, for example `"has 29 cards, needs exactly 30 (2.1)"`.

---

## 3. WebSocket (`/ws/games/{id}?token=…`)

### 3.1 `state`: on connection and on `sync`

What you receive at turn 5. `view` is the full `GameView` (spec §13.3); `history` is every event since the start, redacted for you.

```jsonc
{
  "type": "state",
  "view": {
    "gameId": "8f2c6d1e-3b7a-4c59-9e10-5a4b2d7f9c31",
    "version": 12,
    "status": "in_progress",
    "turn": 5,
    "activePlayer": "you",
    "yourTurn": true,
    "you": {
      "faction": "ember",
      "hp": 50, "maxHp": 50,
      "shards": 3, "maxShards": 3, "lockedNextTurn": 0,
      "fatigue": 0,
      "deckCount": 23,
      "hand": [
        { "id": 3,  "card": "ember.spark-dart",    "cost": 1, "fractureStep": null },
        { "id": 19, "card": "ember.ember-lance",   "cost": 4, "fractureStep": null },
        { "id": 15, "card": "ember.ash-warden",    "cost": 3, "fractureStep": null },
        { "id": 5,  "card": "ember.pyre-offering", "cost": 1, "fractureStep": null },
        { "id": 29, "card": "ember.ashborn-drake", "cost": 6, "fractureStep": null }
      ],
      "mulliganDecided": true,
      "units": [
        {
          "id": 1, "card": "ember.cinderling", "controller": "you", "token": false,
          "defense": 2, "maxDefense": 2,
          "attacks": [
            { "index": 0, "name": "Flick", "cost": 1, "damage": 3, "hasTarget": true, "echo": null }
          ],
          "arrivedThisTurn": false, "attackedThisTurn": false, "interceptedThisTurn": false,
          "frozen": false, "anchorProtected": false, "doomed": false, "linkedTo": null,
          "modifiers": []
        },
        {
          "id": 7, "card": "neutral.shardling", "controller": "you", "token": false,
          "defense": 3, "maxDefense": 3,
          "attacks": [
            { "index": 0, "name": "Glint", "cost": 1, "damage": 3, "hasTarget": true, "echo": null }
          ],
          "arrivedThisTurn": false, "attackedThisTurn": false, "interceptedThisTurn": false,
          "frozen": false, "anchorProtected": false, "doomed": false, "linkedTo": null,
          "modifiers": []
        }
      ],
      "relics": [],
      "graveyard": []
    },
    "opponent": {
      "faction": "root",
      "hp": 50, "maxHp": 50,
      "shards": 0, "maxShards": 2, "lockedNextTurn": 0,
      "fatigue": 0,
      "deckCount": 23,
      "handCount": 5,
      "units": [
        {
          "id": 61, "card": "root.sprout", "controller": "opponent", "token": true,
          "defense": 2, "maxDefense": 2,
          "attacks": [
            { "index": 0, "name": "Prick", "cost": 1, "damage": 2, "hasTarget": true, "echo": null }
          ],
          "arrivedThisTurn": false, "attackedThisTurn": false, "interceptedThisTurn": false,
          "frozen": false, "anchorProtected": false, "doomed": false, "linkedTo": null,
          "modifiers": []
        },
        {
          "id": 36, "card": "root.root-sentinel", "controller": "opponent", "token": false,
          "defense": 5, "maxDefense": 5,
          "attacks": [
            { "index": 0, "name": "Root Slam", "cost": 2, "damage": 4, "hasTarget": true, "echo": null }
          ],
          "arrivedThisTurn": false, "attackedThisTurn": false, "interceptedThisTurn": false,
          "frozen": false, "anchorProtected": true, "doomed": false, "linkedTo": null,
          "modifiers": []
        }
      ],
      "relics": [],
      "graveyard": [ { "id": 31, "card": "root.sproutcaller" } ]
    },
    "decision": {
      "id": "d-13",
      "kind": "main",
      "prompt": "Your turn: play a card, attack, or end your turn.",
      "actions": [
        { "index": 0,  "type": "play", "label": "Play Spark Dart (1 Shard) on Sprout #61", "card": 3, "overcharge": false, "targets": [ { "kind": "unit", "id": 61 } ], "sacrificed": [] },
        { "index": 1,  "type": "play", "label": "Play Spark Dart (1 Shard) on Root Sentinel #36", "card": 3, "overcharge": false, "targets": [ { "kind": "unit", "id": 36 } ], "sacrificed": [] },
        { "index": 2,  "type": "play", "label": "Play Ember Lance overcharged (2 Shards, locks 2 next turn)", "card": 19, "overcharge": true, "targets": [], "sacrificed": [] },
        { "index": 3,  "type": "play", "label": "Play Ash Warden (3 Shards)", "card": 15, "overcharge": false, "targets": [], "sacrificed": [] },
        { "index": 4,  "type": "play", "label": "Play Pyre Offering (1 Shard, sacrifice Cinderling #1) on Sprout #61", "card": 5, "overcharge": false, "targets": [ { "kind": "unit", "id": 61 } ], "sacrificed": [1] },
        { "index": 5,  "type": "play", "label": "Play Pyre Offering (1 Shard, sacrifice Shardling #7) on Sprout #61", "card": 5, "overcharge": false, "targets": [ { "kind": "unit", "id": 61 } ], "sacrificed": [7] },
        { "index": 6,  "type": "play", "label": "Play Pyre Offering (1 Shard, sacrifice Cinderling #1) on Root Sentinel #36", "card": 5, "overcharge": false, "targets": [ { "kind": "unit", "id": 36 } ], "sacrificed": [1] },
        { "index": 7,  "type": "play", "label": "Play Pyre Offering (1 Shard, sacrifice Shardling #7) on Root Sentinel #36", "card": 5, "overcharge": false, "targets": [ { "kind": "unit", "id": 36 } ], "sacrificed": [7] },
        { "index": 8,  "type": "attack", "label": "Cinderling #1 attacks Sprout #61 with Flick (1 Shard)", "attacker": 1, "attackIndex": 0, "targets": [ { "kind": "unit", "id": 61 } ] },
        { "index": 9,  "type": "attack", "label": "Cinderling #1 attacks Root Sentinel #36 with Flick (1 Shard)", "attacker": 1, "attackIndex": 0, "targets": [ { "kind": "unit", "id": 36 } ] },
        { "index": 10, "type": "attack", "label": "Shardling #7 attacks Sprout #61 with Glint (1 Shard)", "attacker": 7, "attackIndex": 0, "targets": [ { "kind": "unit", "id": 61 } ] },
        { "index": 11, "type": "attack", "label": "Shardling #7 attacks Root Sentinel #36 with Glint (1 Shard)", "attacker": 7, "attackIndex": 0, "targets": [ { "kind": "unit", "id": 36 } ] },
        { "index": 12, "type": "end_turn", "label": "End your turn" }
      ]
    },
    "waitingFor": null,
    "result": null
  },
  "history": [
    { "type": "game_started", "rules": ["5.1.1"], "text": "You play first.", "firstPlayer": "you" },
    { "type": "card_drawn", "rules": ["5.1.2"], "text": "You draw Spark Dart.", "player": "you", "card": { "id": 3, "card": "ember.spark-dart" } },
    // … your 4 other opening draws …
    { "type": "card_drawn", "rules": ["5.1.2"], "text": "Your opponent draws a card.", "player": "opponent", "card": null },
    // … the bot's 4 other opening draws, all redacted …
    { "type": "hand_kept", "rules": ["5.1.3"], "text": "You keep your hand.", "player": "you" },
    { "type": "hand_kept", "rules": ["5.1.3"], "text": "Your opponent keeps their hand.", "player": "opponent" },
    { "type": "turn_started", "rules": ["5.2"], "text": "Turn 1: your turn.", "player": "you", "turn": 1 },
    { "type": "shards_refilled", "rules": ["4.1", "4.2"], "text": "You have 1 Shard (max 1).", "player": "you", "max": 1, "available": 1, "locked": 0 },
    // … turns 1 to 4 (see 3.4) …
    { "type": "turn_started", "rules": ["5.2"], "text": "Turn 5: your turn.", "player": "you", "turn": 5 },
    { "type": "shards_refilled", "rules": ["4.1", "4.2"], "text": "You have 3 Shards (max 3).", "player": "you", "max": 3, "available": 3, "locked": 0 },
    { "type": "card_drawn", "rules": ["5.2.3"], "text": "You draw Ashborn Drake.", "player": "you", "card": { "id": 29, "card": "ember.ashborn-drake" } }
  ]
}
```

### 3.2 `act` and `update`

You click "Play Spark Dart (1 Shard) on Sprout #61":

```json
{ "type": "act", "requestId": "c-1", "decisionId": "d-13", "action": 0 }
```

The server applies it and sends one `update` to each seat: the new view (version 13) and that action's events.

```jsonc
{
  "type": "update",
  "view": {
    "version": 13,
    // … same shape as above: Spark Dart has left your hand, you have 2 Shards,
    // Sprout #61 is gone, and "decision" is the new main-phase decision "d-14" …
  },
  "events": [
    { "type": "card_played", "rules": ["6.3"], "text": "You play Spark Dart (1 Shard).", "player": "you", "card": { "id": 3, "card": "ember.spark-dart" }, "cost": 1, "overcharged": false, "fractureStep": null },
    { "type": "damage_dealt", "rules": ["8.1"], "text": "Sprout #61 takes 3 damage.", "target": { "kind": "unit", "id": 61 }, "amount": 3 },
    { "type": "unit_destroyed", "rules": ["6.6"], "text": "Sprout #61 is destroyed.", "unit": { "id": 61, "card": "root.sprout" }, "controller": "opponent", "cause": "zero_defense" },
    { "type": "token_vanished", "rules": ["3.6"], "text": "Sprout #61 vanishes.", "unit": { "id": 61, "card": "root.sprout" } },
    { "type": "spell_resolved", "rules": ["6.3", "3.5"], "text": "Spark Dart goes to your graveyard.", "card": { "id": 3, "card": "ember.spark-dart" } }
  ]
}
```

The other seat gets the same events seen from its side: `"player": "opponent"`, `"text": "Your opponent plays Spark Dart (1 Shard)."`, `"controller": "you"` for the Sprout.

### 3.3 Ending your turn: several bot actions, one `update` each

You choose "End your turn". Your `update` (version 14) carries the end of your turn and the start of the bot's, up to its first decision:

```json
[
  { "type": "turn_ended", "rules": ["5.4"], "text": "You end your turn.", "player": "you" },
  { "type": "turn_started", "rules": ["5.2"], "text": "Turn 6: your opponent's turn.", "player": "opponent", "turn": 6 },
  { "type": "anchor_protection_ended", "rules": ["5.2.1", "11.3.1"], "text": "Root Sentinel #36 is no longer protected by Anchor.", "unit": { "id": 36, "card": "root.root-sentinel" } },
  { "type": "shards_refilled", "rules": ["4.1", "4.2"], "text": "Your opponent has 3 Shards (max 3).", "player": "opponent", "max": 3, "available": 3, "locked": 0 },
  { "type": "card_drawn", "rules": ["5.2.3"], "text": "Your opponent draws a card.", "player": "opponent", "card": null }
]
```

Its view has `"decision": null` and `"waitingFor": { "player": "opponent", "kind": "main" }`. Then each bot action produces its own `update` (version 15, 16…), until the decision is yours again.

### 3.4 An intercept, in a game between two humans

Your Cinderling #1 attacks Root Sentinel #36. Both seats receive:

```json
{ "type": "attack_declared", "rules": ["7.4"], "text": "Cinderling #1 attacks Root Sentinel #36 with Flick.", "attacker": { "id": 1, "card": "ember.cinderling" }, "attackIndex": 0, "target": { "kind": "unit", "id": 36 } }
```

Your view: `"decision": null, "waitingFor": { "player": "opponent", "kind": "intercept" }`. Your opponent's view, during your turn:

```json
{
  "id": "d-14",
  "kind": "intercept",
  "prompt": "Cinderling #1 attacks your Root Sentinel #36 with Flick (3 damage). Intercept with another unit?",
  "actions": [
    { "index": 0, "type": "decline_intercept", "label": "Don't intercept" },
    { "index": 1, "type": "intercept", "label": "Intercept with Sprout #61", "interceptor": 61 }
  ]
}
```

If they intercept: `{ "type": "attack_intercepted", "rules": ["7.5"], "text": "Sprout #61 intercepts the attack.", "originalTarget": { "id": 36, "card": "root.root-sentinel" }, "interceptor": { "id": 61, "card": "root.sprout" } }`, then the damage events on Sprout #61. If they decline: `{ "type": "intercept_declined", "rules": ["7.5"], "text": "Your opponent does not intercept.", "player": "opponent" }` *(see [Decisions](#decisions-taken-on-these-examples), point 5)*.

### 3.5 `rejected`

Sent only to the sender. For example, a click on an old decision after a reconnection:

```json
{ "type": "rejected", "requestId": "c-2", "reason": "stale_decision", "message": "Decision d-13 is no longer pending." }
```

Other reasons: `not_your_decision`, `invalid_action`, `malformed_message`, `game_not_started`, `game_over`.

### 3.6 Before the opponent joins, and after the end

The creator of a game against a human, before the second player joins:

```json
{
  "gameId": "8f2c6d1e-…", "version": 0, "status": "waiting_for_opponent",
  "turn": 0, "activePlayer": null, "yourTurn": false,
  "you": { "faction": "ember", "hp": 50, "maxHp": 50, "shards": 0, "maxShards": 0, "lockedNextTurn": 0, "fatigue": 0, "deckCount": 30, "hand": [], "mulliganDecided": false, "units": [], "relics": [], "graveyard": [] },
  "opponent": null,
  "decision": null, "waitingFor": null, "result": null
}
```

When the game is over, the last `update` carries `"status": "finished"`, `"decision": null`, and for example `"result": { "outcome": "win", "reason": "hp" }`, with a final event `{ "type": "game_ended", "rules": ["1.2"], "text": "Your opponent drops to 0 HP. You win.", "outcome": "win", "reason": "hp" }`.

---

## Decisions taken on these examples

Reviewed with the maintainer on 2026-10-05.

1. **Instance ids.** A card id (`root.sprout`) names a card; an instance id (`#61`) names one physical copy in one game. The engine needs both: two Sprouts or two Spark Darts must be told apart in actions ("attack Sprout #61", not "attack a Sprout"), in links, in Fracture progress, in the log and when the bots track known cards. Ids come from **one counter per game**: your 30 cards are #1–#30 and the opponent's #31–#60, in decklist order, assigned before the shuffle; then every token takes the next number when it is created, whoever summons it (#61, #62…). An id then never reveals more than the card it labels, as long as redacted events drop it (§1.4); assigning ids after the shuffle would leak which cards were in the opening hand.
2. **No unit names in the views.** `UnitView` and `CardRef` carry the card id only; the client takes names and texts from `GET /api/cards`, loaded once. Event `text` and action `label` already contain names.
3. **Hand cards carry their current cost** (`HandCardView.cost`, added to spec §13.3): printed cost, or the next Fracture step's cost, plus cost auras (6.8), without Overcharge. The overcharged price is in the action label.
4. **`DamageDealt.amount` is the damage dealt**, not the defense actually removed: 3 on a 2-defense Sprout, which ends at 0 (6.9). The view gives the new defense.
5. **`InterceptDeclined`** (added to spec §6.4): when an attack targets one of your units and you have a unit able to intercept, you are asked; choosing "Don't intercept" logs this event. Without it, the attacker's log would jump from "attacks" to "takes damage" with no trace of the choice their opponent made. No eligible interceptor: no question, no event.
6. **Optional fields**: left out of REST responses when absent (`cost` of a token), explicit `null` in the WebSocket views, as typed in spec §13.3.
7. **`gameId` is a UUID**; seat tokens and join codes are 32 random bytes in base64url (43 characters).
