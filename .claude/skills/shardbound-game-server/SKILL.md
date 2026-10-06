---
name: shardbound-game-server
description: How Shardbound's game server (engine/api) and its Angular test client (frontend/) work and how to change them — the REST resources, the WebSocket game protocol (state, update, rejected, act, sync, versions, close code 4409), game sessions built for several instances (GameRepository with versioned saves, GameUpdates notifications, the outbox, PlayerConnections), bots rebuilt at every move, the wire format of views and events, and the client's socket service, update pacing and grouped decision buttons. Use this whenever you touch engine/api or frontend/, add a REST endpoint, a protocol message, a view field, a bot or a storage adapter, debug a game that does not update, a rejected message or a reconnection, test the server or the client, or explain how a click in the browser reaches the engine — even for a one-line change. For the engine itself, use shardbound-engine and shardbound-engine-dev.
---

# The game server and the test client

`engine/api` puts the engine (`engine/core`) on the network, and `frontend/` is the minimal client that plays through it. Neither decides anything about the game. The server stores games, checks who may answer, and translates; the client renders and clicks. Every rule, sentence, label and number a player sees comes from the engine: views (`engine.view`), redacted events (`engine.eventsFor`), `EventDescriber`, `ActionDescriber`, `DecisionDescriber`, `AttackDamage`, `Costs`.

The contract is `specs/phase-2-engine.md` §12 (REST) and §13 (protocol, DTOs, sessions); `specs/phase-2-examples.md` shows real payloads. Read §13.4 before changing sessions.

## Vocabulary

| Word | What it is | Type |
|---|---|---|
| Session | Everything about one game, saved between two messages: setup, seats, state, logs, outbox, version | `session.GameSession` |
| Seat | P1 or P2 and who sits there: a human (token hash), a bot (name + generator state), or an open seat (join code hash) | `session.Seat` |
| Version | +1 at every save of the session: each applied action, and the join of a human game | `GameSession.version` |
| Outbox | Each human seat's `update` message (JSON) for the last 50 versions, saved with the session | `session.Outbox` |
| Notification | "game X is at version n", nothing more | `GameUpdates.publish` |
| Connection | One seat's live socket on this instance | `session.SeatConnection` |
| `state` | Full view + whole history, redacted for the seat: on connection and on `sync` | `protocol.ServerMessage.State` |
| `update` | View after one save + that save's events | `protocol.ServerMessage.Update` |
| `rejected` | To the sender only: `stale_decision`, `not_your_decision`, `invalid_action`, `malformed_message`, `game_not_started`, `game_over` | `protocol.ServerMessage.Rejected` |

## The model in six points

1. **No instance owns a game** (spec §13.4). Cloud Run may route two messages of one game, or the two players, to different instances. So every change loads the session from `GameRepository`, applies it, and saves it with `save(session, expectedVersion)`, an optimistic lock. A `VersionConflict` means another instance moved first: reload and start again; the request usually ends as `stale_decision`.
2. **Notifications carry no data.** After a save, `GameUpdates.publish(game, version)`. Each instance holding a connection to that game reads, from the session's outbox, the updates that connection has not received (`version > sent`) and sends them in order. A lost notification costs nothing: the next one, or a `sync`, catches up. A connection more than 50 versions behind gets the `state` instead.
3. **The client is never trusted.** `act(game, seat, decisionId, index)` recomputes the decision from the saved state, then checks, in order: game started, not over, `decisionId` current (else `stale_decision`), seat is the decider (else `not_your_decision`), index in range (else `invalid_action`). The client sends an index; it never builds an action.
4. **Bots are rebuilt at every move** from their seat: `BotRoster.rebuild(seat)` makes a `Bot` from its name and `rngState`, it picks from its own `PlayerView`, and the new generator state is saved with the move. One save and one `update` per bot move. A bot's first state comes from the game seed, so a bot game replays from its setup and the human's actions. Whoever loads a session whose decision is a bot's runs the loop (`resumeBots`: on connection and on `sync`).
5. **One connection per seat.** A new connection announces itself (`GameUpdates.connected`); whichever instance holds an older one for that seat calls `SeatConnection.replaced()`, which closes the WebSocket with 4409.
6. **Nothing about a game lives in an instance's memory.** `PlayerConnections` holds only this instance's sockets and, per socket, the last version sent. `GameLocks` (striped, per game) only avoids pointless conflicts inside one instance; correctness comes from the versioned save.

## Where things live

`engine/api/src/main/java/fr/daliush/shardbound/api/`:

| Package | Contents |
|---|---|
| `session` | `GameSession`, `Seat`, `Outbox`, `GameSessionService` (create, join, authenticate, state, act, resumeBots), `PlayerConnections` and the `SeatConnection` port, the ports `GameRepository` and `GameUpdates` with `GameRepositoryInMemory` and `GameUpdatesInMemory`, `BotRoster`, `SeatTokens`, `GameLocks`, `SessionMessages`, `SessionException` |
| `protocol` | `ServerMessage`, `ClientMessage`, `ProtocolJson` (explicit `null`s) |
| `dto` | The wire records of spec §13.3 and their mappers: `GameViews` (view → `GameView`), `DecisionViews` (prompt, labels, actions), `EventViews` (events one to one, generic), `Wire` (sides, snake_case) |
| `rest` | `ContentController` (cards, decks, bots), `GamesController`, `ApiExceptionHandler` (Problem Details) |
| `ws` | `GameHandshakeInterceptor` (token → seat, or 404 / 401), `GameWebSocketHandler` (one virtual thread per connection, messages in order), `WebSocketSeatConnection` (Spring's thread-safe decorator), `GameSockets` |
| `config` | `EngineConfig` (content, engine), `SessionConfig` (adapters, service, eviction), `WebSocketConfig`, `ShardboundProperties` |

`session` knows no transport and no Spring, so its tests run two instances in one JVM. Ports are interfaces; implementations add a suffix: `…InMemory` now, `…Database` later.

`frontend/src/app/`: `api/` (the contract in TypeScript, `ApiClient`, `SeatStore`), `game/` (`GameSocketService`, `DecisionGroups`, `GamePage` and its tiles, panel and log), `home/`, `join/`, `testing/` (a fake WebSocket and sample views).

## The wire format, in short

- **Views**: data from the engine's `PlayerView` only; the full `GameState` is read by the describers, for the seat's own decision. Fields typed `T | null` in §13.3 are always written (WebSocket); fields typed `field?` are left out when absent (`ActionView`, `TargetView`, and every REST body).
- **Events**: one to one with the engine's records (`EventViews`): `type` is the record name in snake_case, then `rules`, `text` (`EventDescriber`, from the seat's point of view), then the record's components. Players become `"you"` / `"opponent"`, cards `{ id, card }`, targets `{ kind, id, card }` or `{ kind, player }`, results `{ outcome, reason }`. A new engine event needs no change here.
- **Secrets**: seat tokens and join codes are 32 random bytes in base64url, stored as SHA-256 hashes, compared in constant time. The seed is never returned.

## The client, in short

- `GameSocketService` (one per game page) holds the connection (RxJS `webSocket()`) and exposes `view`, `log`, `rejection` and `status` as signals. It applies `update`s in version order, one every 400 ms so a bot's turn can be followed (the server keeps `step-delay` at 0). A version gap clears the queue and sends `sync`. A dropped connection reconnects with a backoff, and the `state` the server sends on connection resyncs it. Close code 4409 stops it for good.
- `DecisionGroups` turns the decision's actions into clicks: sources (the card played, the attacker, the interceptor) are highlighted; selecting one lights up its targets and shows its target-less actions as buttons; a target click sends the action if only one matches, otherwise the matches become buttons. Actions without a source (keep hand, mulligan, end turn, don't intercept) are plain buttons.
- Tokens live in `localStorage` (`shardbound.game.<id>.token`), so a reload resumes the game. Every tab of a browser shares them: two seats on one machine need two browsers, a private window, or a second dev server (`npm start -- --port 4201`).

## Changing it

Read `references/recipes.md` for step-by-step changes: a REST endpoint, a protocol message, a view or event field, a bot, a storage adapter, a client screen. `references/flows.md` follows one click through the system, a game between two instances, and a reconnection, step by step.

Rules of the house, on top of the engine skills' code style:

- **The server translates, it never computes a rule.** If a client needs a number or a sentence the engine does not give, add it to core (a describer, a function next to `Costs`), not to `dto`.
- **Talk to a seat only through its view and redacted events.** Mappers take what the engine lets the seat see; `EventViews.of` redacts by itself.
- **Every change of a session is a save with its expected version, followed by a publish.** Never send to a connection directly from the service; put the message in the outbox.
- **Messages are data.** Records in `protocol` and `dto`, no behavior beyond small factories.

## Testing

| Test | What it proves |
|---|---|
| `session.GameSessionServiceTest` | A bot game to the end with one save and one notification per action, and the action log replaying the state; stale, foreign and invalid answers; the join as version 1; a mid-turn intercept between humans; `sync` |
| `session.TwoInstancesTest` | Two services sharing one repository and one `GameUpdates`: humans on different instances receive every version in order; the same answer sent to both at once is applied once (`stale_decision` for the other); a bot game alternating instances; replacement across instances |
| `session.GameSessionJsonTest` | A session survives a JSON round trip (`GameJson.writeValue`), so a shared store can hold it |
| `dto.ViewsTest` | Redaction in views, explicit nulls, action fields, and every event of 30 random games written one to one |
| `rest.RestApiTest` | MockMvc: resources, status codes, Problem Details |
| `ws.GameWebSocketTest` | A real client on a random port: `state`, `update`, `rejected`, 401 / 404 handshakes, 4409 |
| `frontend` (Vitest) | The socket service (update, pacing, gap → sync, rejected, reconnection, 4409), `DecisionGroups`, a smoke test per page |

Run `./mvnw verify` in `engine/`, and `npm test` then `npm run build` in `frontend/`.

## Running and debugging

```bash
cd engine && ./mvnw -pl api -am spring-boot:run   # http://localhost:8080; one log line per applied action
cd frontend && npm start                          # http://localhost:4200, proxies /api and /ws
```

- Each applied action logs `game=… seat=… decision=… action="…" version=… instance=…`. Tests silence it in `logback-test.xml`.
- A client stuck on an old view: compare its `view.version` with the session's; a gap should have sent `sync`.
- A `rejected` you did not expect: the reason says which check failed (point 3 above).
- `GameSessionService.MAX_BOT_MOVES` (10,000 in a row) fails loudly if a bot loop never gives the decision back.

## Docs to keep in sync

A protocol or session change updates spec §12–§14 and, when payloads move, `specs/phase-2-examples.md`. The end of a slice updates the `docs/design.md` changelog, `CLAUDE.md` and the `shardbound-project` skill. This skill changes with the server.
