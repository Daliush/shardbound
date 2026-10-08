---
name: shardbound-game-server
description: How Shardbound's game server (engine/api) and its Angular test client (frontend/) work and how to change them — the layers (controller → domain ← adapter → dao, checked by ArchUnit), the REST resources, the WebSocket game protocol (state, update, rejected, act, sync, versions, close code 4409), game sessions built for several instances (versioned saves through GameSessionPort, notifications through GameNotificationPort, the outbox, GameSocketRegistry), bots rebuilt at every move, the wire format of views and events, and the client's socket service, update pacing and grouped decision buttons. Use this whenever you touch engine/api or frontend/, add a REST endpoint, a protocol message, a view field, a bot or a DAO, debug a game that does not update, a rejected message or a reconnection, test the server or the client, decide which layer code belongs to, or explain how a click in the browser reaches the engine — even for a one-line change. For the engine itself, use shardbound-engine and shardbound-engine-dev.
---

# The game server and the test client

`engine/api` puts the engine (`engine/core`) on the network, and `frontend/` is the minimal client that plays through it. Neither decides anything about the game. The server stores games, checks who may answer, and translates; the client renders and clicks. Every rule, sentence, label and number a player sees comes from the engine: views (`engine.view`), redacted events (`engine.eventsFor`), `EventDescriber`, `ActionDescriber`, `DecisionDescriber`, `AttackDamage`, `Costs`.

The contract is `specs/phase-2-engine.md` §12 (REST) and §13 (protocol, DTOs, sessions); `specs/phase-2-examples.md` shows real payloads. Read §13.4 before changing sessions.

## The layers

```
controller ──▶ domain ◀── adapter ──▶ dao
```

| Layer | Holds | Knows |
|---|---|---|
| `controller` | The endpoints: `rest/` (controllers, `dto/`), `ws/` (handshake, handler, `GameSocketRegistry`, `SeatSocket`, `message/`), `mappers/` (DTOs and messages ↔ business objects, the protocol JSON) | the domain only |
| `domain` | `bo/` (business objects: `game`, `view`, `command`, `error`), `services/` (`game`, `update`, `bot`, `security`, `content`), `mappers/view` (engine → what a seat may see), `ports/` (the interfaces it needs) | nothing but itself and core; no transport, no JSON |
| `adapter` | The ports implemented on the DAOs (`game/GameSessionAdapter`, `notification/GameNotificationAdapter`), and the entity ↔ business object mapping (`mappers/game`) | the domain and the DAOs |
| `dao` | Pure data: `entities/`, `game/GameDao`, `notification/GameNotificationDao`, each with an `*InMemory` implementation (later `*Database`) | nothing above it |

`config` wires them, outside the layers. `ArchitectureTest` (ArchUnit) fails the build if a layer reaches the wrong way, or if the domain imports Spring Web, servlets or Jackson. Services, mappers and adapters take their collaborators through their constructors, so a test can wire a whole instance by hand (`testing.TestInstance`).

## Vocabulary

| Word | What it is | Type |
|---|---|---|
| Session | Everything about one game, saved between two messages: seed, seats, state, logs, outbox, version | `domain.bo.game.GameSession` |
| Seat | P1 or P2 and who sits there: a human (token hash), a bot (name + generator state), or an open seat (join code hash) | `domain.bo.game.Seat` |
| Version | +1 at every save of the session: each applied action, and the join of a human game | `GameSession.version` |
| Snapshot | What one seat may see of a state: the engine's `PlayerView`, plus what each card of its hand costs (cost auras read both boards) and the prompt and labels of its own decision | `domain.bo.game.SeatSnapshot` |
| Outbox | Each human seat's `SeatUpdate` (snapshot + redacted events) for the last 50 versions, saved with the session | `domain.bo.game.Outbox` |
| Notification | "game X is at version n", nothing more | `GameNotificationPort.publish` |
| Socket | One seat's live WebSocket on this instance | `controller.ws.SeatSocket` |
| `state` / `update` / `rejected` | Full view + whole history / view after one save + its events / to the sender only | `controller.ws.message.ServerMessage` |

## The model in six points

1. **No instance owns a game** (spec §13.4). Cloud Run may route two messages of one game, or the two players, to different instances. So every change loads the session through `GameSessionPort`, applies it, and saves it with `save(session, expectedVersion)`, an optimistic lock that answers false when another instance moved first: reload and start again; the request usually ends as `stale_decision`.
2. **Every change ends in `GameSaver`**: each human seat's `SeatUpdate` goes to the outbox (the prompt and labels are written now, since the describers read the full state), the session is saved, then `GameNotificationPort.publish(game, version)`. Notifications carry no data: each instance holding a socket to the game asks `SeatUpdateService.since(game, seat, sent)` for what that socket has not received, and sends it in order. A lost notification costs nothing; a socket more than 50 versions behind gets the `state`.
3. **The client is never trusted.** `GamePlayService.act(game, seat, decisionId, index)` recomputes the decision from the saved state, then checks, in order: game started, not over, `decisionId` current (else `stale_decision`), seat is the decider (else `not_your_decision`), index in range (else `invalid_action`). The client sends an index; it never builds an action.
4. **Bots are rebuilt at every move** by `BotTurnService`: `BotRoster.rebuild(seat)` makes a `Bot` from its name and `rngState`, it picks from its own `PlayerView`, and its new generator state is saved with the move. One save and one update per bot move. A bot's first state comes from the game seed, so a bot game replays from its setup and the human's actions. Whoever opens a socket to a game left on a bot's decision resumes the loop.
5. **One socket per seat.** A new socket is announced (`GameNotificationPort.announce`); whichever instance holds an older one for that seat gets `GameWatcher.seatTaken` and closes it with 4409.
6. **Nothing about a game lives in an instance's memory.** `GameSocketRegistry` holds only this instance's sockets and, per socket, the last version sent. `GameLocks` (striped, per game) only avoids pointless conflicts inside one instance; correctness comes from the versioned save.

## The wire format, in short

- **Views** are domain business objects built from the engine's `PlayerView` and the snapshot's texts (`domain.mappers.view`). They carry no JSON annotation: `controller.mappers.ws.ProtocolJson` says, with mix-ins, that empty optionals are written as `null`, that `ActionView`, `TargetView` and `EventTargetView` only carry their own fields, and that an event's fields sit beside its type.
- **Events** are one to one with the engine's records (`EventViewMapper`, which redacts first): `type` is the record name in snake_case, then `rules`, `text` (`EventDescriber`, from the seat's point of view), then the record's components. A new engine event needs no change.
- **Secrets**: seat tokens and join codes are 32 random bytes in base64url, stored as SHA-256 hashes, compared in constant time (`SeatTokens`). The seed is never returned.
- **REST** bodies leave optional fields out; errors are Problem Details (`ApiExceptionHandler` maps each `GameException`). `GET /api/cards` carries each card's `text` from core's `CardTextRenderer`, through `ContentService`.

## The client, in short

- `GameSocketService` (one per game page) holds the connection (RxJS `webSocket()`) and exposes `view`, `log`, `rejection` and `status` as signals. It applies `update`s in version order, one every 400 ms so a bot's turn can be followed (the server keeps `step-delay` at 0). A version gap clears the queue and sends `sync`. A dropped connection reconnects with a backoff, and the `state` the server sends on connection resyncs it. Close code 4409 stops it for good.
- `DecisionGroups` turns the decision's actions into clicks: sources (the card played, the attacker, the interceptor) are highlighted; selecting one lights up its targets and shows its target-less actions as buttons; a target click sends the action if only one matches, otherwise the matches become buttons (with and without Overcharge, one per sacrifice). A `choose_cards` action naming one card or unit is clicked on it (`locateIn` finds it in the view); combinations stay buttons.
- Card texts come from `GET /api/cards` (`CardBook.text`), one line per entry with its kind: a hand card and a relic (`RelicTile`) show every line, a unit only its keywords and abilities, each attack's line being its tooltip. The unit tile also shows each modifier as a badge.
- Tokens live in `localStorage` (`shardbound.game.<id>.token`), so a reload resumes the game. Two seats on one machine need two browsers, a private window, or a second dev server (`npm start -- --port 4201`).

## Changing it

Read `references/recipes.md` for step-by-step changes: a REST endpoint, a view or event field, a protocol message, a bot, a new DAO, a client screen. `references/flows.md` follows one click through the layers, a game between two instances, and a reconnection.

Rules of the house, on top of the engine skills' code style:

- **Put code in its layer.** An endpoint, a DTO or a message: `controller`. A rule of hosting a game (who may answer, what a seat may see, when to save): `domain`. How a port is implemented, entity mapping: `adapter`. A map, a SQL query, a pub/sub call: `dao`. The controller never calls an adapter or a DAO.
- **The server translates, it never computes a rule.** If a client needs a number or a sentence the engine does not give, add it to core (a describer, a function next to `Costs`).
- **Talk to a seat only through its view and redacted events.** Mappers take what the engine lets the seat see.
- **Every change of a session goes through `GameSaver`.** Never send to a socket from a service; the outbox and the notification do it.

## Testing

| Test | What it proves |
|---|---|
| `domain.services.game.GamePlayServiceTest` | A bot game to the end with one save and one notification per action, the action log replaying the state; stale, foreign and invalid answers; a mid-turn intercept between humans |
| `domain.services.game.GameCreationServiceTest` | The join as version 1; refused joins; unknown decks, bots, games and tokens |
| `domain.services.update.SeatUpdateServiceTest` | A seat's state, redacted; a waiting game; the updates it missed |
| `domain.mappers.view.ViewMappersTest`, `domain.bo.game.OutboxTest` | What each seat sees; every event of 30 random games written one to one; the outbox's window |
| `adapter.game.GameSessionAdapterTest` | A session comes back unchanged through the entity mapper and the DAO's JSON; the optimistic lock |
| `controller.ws.TwoInstancesTest` | Two instances wired by hand on shared DAOs, with fake WebSockets: every version in order across instances, the same answer applied once, replacement across instances |
| `controller.mappers.ws.ProtocolJsonTest`, `controller.rest.RestApiTest`, `controller.ws.GameWebSocketTest` | The JSON rules; REST with MockMvc and Problem Details; a real WebSocket client on a random port |
| `ArchitectureTest` | The layers |
| `frontend` (Vitest) | The socket service, `DecisionGroups`, a smoke test per page |

Run `./mvnw verify` in `engine/`, and `npm test` then `npm run build` in `frontend/`.

## Running and debugging

```bash
cd engine && ./mvnw -pl api -am spring-boot:run   # http://localhost:8080; one log line per applied action
cd frontend && npm start                          # http://localhost:4200, proxies /api and /ws
```

- Each applied action logs `game=… seat=… decision=… action="…" version=… instance=…` (`GameSaver`). Tests silence it in `logback-test.xml`.
- A client stuck on an old view: compare its `view.version` with the session's; a gap should have sent `sync`.
- A `rejected` you did not expect: the reason says which check failed (point 3 above).
- `BotTurnService.MAX_BOT_MOVES` (10,000 in a row) fails loudly if a bot loop never gives the decision back.

### Play-testing in the browser pane

To check a change in the real client, run `docker compose up --build` from the root (it replaces a stack already running) and open http://localhost:4200 in the browser pane.

- **A seeded game**: from the page, `fetch('/api/games', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ deck, opponent: { type: 'bot', bot, deck: botDeck }, seed }) })`, store `playerToken` under `shardbound.game.<gameId>.token` in `localStorage`, then go to `/games/<gameId>`. The creator is P1, and P1's draws depend only on the seed: to pick a seed with a good opening, print P1's hand and the top of its deck after `newGame` for a range of seeds in a throwaway core test.
- **The page**: the board's cards and units are `button.tile`, with the class `selectable` (a source to click), `selected` or `targetable`; each player's panel is a `button.panel`, `targetable` when an attack or effect can hit that player; the decision's actions are plain buttons labelled by the engine (`End your turn`, `Play … overcharged (…)`, `Don't intercept`…), and `button.cancel` drops a selection. `get_page_text` reads the board, the prompt and the log, whose lines start with their rule IDs.
- **Clicking**: while the pane is hidden, mouse clicks do not land; click through the DOM (`element.click()`) with `javascript_tool`. Updates arrive one every 400 ms, so wait a second or more after each action, longer for a bot's turn. A `javascript_tool` call times out after 45 s: drive long sequences in several calls.

## Docs to keep in sync

A protocol or session change updates spec §12–§14 and, when payloads move, `specs/phase-2-examples.md`. The end of a slice updates the `docs/design.md` changelog, `CLAUDE.md` and the `shardbound-project` skill. This skill changes with the server.
