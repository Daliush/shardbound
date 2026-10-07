# Flows, step by step

How a message travels through the layers of the game server. The code is in `engine/api/src/main/java/fr/daliush/shardbound/api/` and `frontend/src/app/`.

## Contents

1. Creating a game against a bot
2. One click, against a bot
3. Two humans on two instances, with an intercept
4. A reload, a lost notification, a replaced socket

## 1. Creating a game against a bot

```
POST /api/games {deck, opponent: {type: bot, bot: random, deck}, seed?}
controller  GameController.create → GameRequestMapper.toNewGame
domain      GameCreationService.create
              legal decks (unknown or illegal → GameException.InvalidRequest → 400)
              seat P1 = Human(deck, hash(token)); seat P2 = Bot("random", firstState(seed))
              engine.newGame → GameSession.started (version 0)
adapter       GameSessionPort.create → GameSessionAdapter: GameEntityMapper.toEntity
dao             GameDaoInMemory.create: version, status and JSON in a row
domain        BotTurnService.resume: if the bot decides first (its mulligan), it plays now (versions 1, 2…)
controller  201 {gameId, playerToken, websocketPath} via GameRequestMapper.toResponse; no joinCode, no seed
```

The client saves the token in `localStorage` and opens `/games/:id`.

## 2. One click, against a bot

```
browser     GamePage: click a card, then a target → DecisionGroups.pick → index 0
            GameSocketService.act → {"type":"act","requestId":"c-1","decisionId":"d-13","action":0}
controller  GameWebSocketHandler: the socket's own virtual thread runs answer(...)
domain      GamePlayService.act (game lock): load v12 through the port, recompute the decision, checks pass
            GameSaver.apply: engine.apply → session v13; each human seat's SeatUpdate into the outbox
adapter       GameSessionAdapter.save(v13, expected 12) → dao GameDaoInMemory.save ✓
domain        GameNotificationPort.publish(game, 13)
adapter       GameNotificationAdapter → dao GameNotificationDaoInMemory: calls this instance's listener
adapter       its Forwarding listener → domain GameWatcher.moved(game, 13)
controller  GameSocketRegistry.moved → SeatUpdateService.since(game, P1, 12) → [UpdateView v13]
            ServerMessageMapper.update → ProtocolJson → SeatSocket.send
domain      back in act: BotTurnService.resume → is the decision a bot's? no → done
browser     ← update v13, applied at once (the queue was empty)
```

When the click ends the turn, `BotTurnService` runs right after: one save, one notification and one `update` per bot move (v14, v15…), until the decision is the human's again. The client queues them and shows one every 400 ms.

## 3. Two humans on two instances, with an intercept

P1's socket is on instance A, P2's on instance B; both share the game DAO and the notification DAO.

```
B: join (version 1)       P2 takes the open seat; engine.newGame; GameSaver.save v1; publish(1)
A: GameSocketRegistry     P1 was waiting at v0 → since(0) gives v1: the setup and P1's mulligan
B: P2 connects            handshake: SeatAuthenticationService → registry.open → announce(P2) → state v1
...
A: P1 attacks             save v20: the paused attack step and an INTERCEPT decision for P2
                          publish(20) → A sends P1 "waiting for your opponent (intercept)"
                                      → B sends P2 the view with the intercept decision
B: P2 intercepts          GamePlayService.act on B: load v20, checks, apply, save v21, publish(21)
A and B                   each registry sends its own seat the v21 update, redacted for that seat
```

`controller.ws.TwoInstancesTest` plays this with fake WebSockets and checks that each socket received every version once, in order.

## 4. A reload, a lost notification, a replaced socket

- **Reload.** The page reads the token from `localStorage` and connects again. The handshake finds the seat. `GameSocketRegistry.open` registers the socket (and watches the game), announces it, then, under the socket's lock, reads and sends the `state`: the full view and the whole history. Updates saved meanwhile wait for the lock and go out after it, from the outbox.
- **Lost notification.** Nothing is lost but time: the next notification sends every missing version from the outbox. A client that sees `version ≠ last + 1` sends `sync` and gets the `state`.
- **Replaced socket.** A second socket for the same seat, on any instance, is announced; the instance holding the first one gets `seatTaken` and closes it with 4409. The client then shows "open in another tab or window" and does not reconnect.
