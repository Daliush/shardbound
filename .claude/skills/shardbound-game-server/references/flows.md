# Flows, step by step

How a message travels through the game server. The code is in `engine/api/src/main/java/fr/daliush/shardbound/api/` and `frontend/src/app/`.

## Contents

1. Creating a game against a bot
2. One click, against a bot
3. Two humans on two instances, with an intercept
4. A reload, a lost notification, a replaced connection

## 1. Creating a game against a bot

```
POST /api/games {deck, opponent: {type: bot, bot: random, deck}, seed?}
  GamesController.create
    GameSessionService.create
      legalDeck(×2)                    unknown or illegal deck → InvalidRequest → 400
      seat P1 = Human(deck, SHA-256(token)); seat P2 = Bot("random", firstState(seed))
      engine.newGame → GameSession.started (version 0) → repository.create
      resumeBots                       if the bot decides first (its mulligan), it plays now: versions 1, 2…
  201 {gameId, playerToken, websocketPath}      no joinCode, no seed
```

The client saves the token in `localStorage` and opens `/games/:id`.

## 2. One click, against a bot

```
browser                    instance                                   repository / updates
-------                    --------                                   --------------------
GamePage: click a card, then a target
  DecisionGroups.pick → one action, index 0
  GameSocketService.act → {"type":"act","requestId":"c-1","decisionId":"d-13","action":0}
                           GameWebSocketHandler: hands it to the connection's virtual thread
                           GameSessionService.act (game lock)
                             load v12; recompute the decision; checks pass
                             engine.apply → session v13, outbox += update(v13) per human seat
                                                                       save(v13, expected 12) ✓
                                                                       publish(game, 13)
                           PlayerConnections.updated → outbox.after(12, 13, P1) → send
                             playBots: the bot holds the decision? no → done
← update v13 (applied at once: the queue was empty)
```

When the click ends the turn, `playBots` runs right after: one save, one publish and one `update` per bot move (v14, v15…), until the decision is the human's again. The client queues them and shows one every 400 ms.

## 3. Two humans on two instances, with an intercept

P1's socket is on instance A, P2's on instance B; both share the repository and the pub/sub.

```
B: join (version 1)       P2 takes the open seat; engine.newGame; save v1; publish(1)
A: PlayerConnections      P1 was waiting at v0 → sends outbox update v1: the setup and P1's mulligan
B: P2 connects            authenticate (handshake) → register → connected(P2) → state v1
...
A: P1 attacks             save v20: the paused attack step and an INTERCEPT decision for P2
                          publish(20) → A sends P1 "waiting for your opponent (intercept)"
                                      → B sends P2 the view with the intercept decision
B: P2 intercepts          act on B: load v20, checks, apply, save v21, publish(21)
A and B                   each sends its own seat the v21 update, redacted for that seat
```

`TwoInstancesTest` plays this and checks that each connection received every version once, in order.

## 4. A reload, a lost notification, a replaced connection

- **Reload.** The page reads the token from `localStorage` and connects again. The handshake finds the seat. `PlayerConnections.open` registers the connection, announces it (`connected`), then, under the connection's lock, reads and sends the `state`: the full view and the whole history. Updates saved meanwhile wait for the lock and go out after it, from the outbox.
- **Lost notification.** Nothing is lost but time: the next notification sends every missing version from the outbox. A client that sees `version ≠ last + 1` sends `sync` and gets the `state`.
- **Replaced connection.** A second socket for the same seat, on any instance, triggers `connected`; the instance holding the first one closes it with 4409. The client then shows "open in another tab or window" and does not reconnect.
