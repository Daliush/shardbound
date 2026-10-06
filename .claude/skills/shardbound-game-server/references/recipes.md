# Game server recipes

Step-by-step changes. Server paths are relative to `engine/api/src/main/java/fr/daliush/shardbound/api/`, its tests to `engine/api/src/test/java/fr/daliush/shardbound/api/`, client paths to `frontend/src/app/`.

## Contents

1. Add a REST endpoint
2. Add a field to a view
3. A new engine event or action
4. Add a protocol message
5. Add a bot
6. Add a storage adapter (the shared store)
7. Change the client

## 1. Add a REST endpoint

1. Spec §12 first: path, request, response, errors.
2. A method on `rest/ContentController` (read-only content) or `rest/GamesController` (games), or a new controller if it is a new resource. Request and response records live next to it (see `GameRequests`), with Jakarta validation on requests and `@JsonInclude(NON_NULL)` on responses: optional fields are left out of REST bodies.
3. Errors: throw a `session.SessionException` subtype; `ApiExceptionHandler` maps it to Problem Details. A new kind of refusal is a new subtype, and the compiler asks for its status in the handler's `switch`.
4. Test in `rest/RestApiTest` with MockMvc: status, body, Problem Details.
5. Client: a method on `api/api-client.ts` and its types in `api/protocol.ts`.

## 2. Add a field to a view

1. Is it the engine's? A number or a sentence the client needs must come from core: add it there first (`shardbound-engine-dev`), next to `Costs`, `AttackDamage` or the describers.
2. Spec §13.3: the field and its type. `T | null` is always written; `field?` is left out when absent.
3. The record in `dto/` and its mapper (`GameViews` or `DecisionViews`). Read it from the seat's `PlayerView`, never from another player's state.
4. `dto/ViewsTest`, and the type in `api/protocol.ts`, then render it.

## 3. A new engine event or action

- **Event**: nothing to do. `dto/EventViews` writes every event from its record components. If a component has a type `EventViews.wire` does not know, the random games of `ViewsTest` fail: add a case there.
- **Action type**: a case in `dto/DecisionViews.action` and a factory in `dto/ActionView`; the `type` string in spec §13.3 and in `api/protocol.ts`; in `game/decision-groups.ts`, its source in `sourceOf` if a card or unit performs it. Its targets need nothing if they are units, relics or players.
- **Decision kind**: its prompt in core's `DecisionDescriber`, and its string in `api/protocol.ts`. The waiting message uses the kind as it is.

## 4. Add a protocol message

1. Spec §13.2.
2. Client to server: a record in `protocol/ClientMessage` and its `@JsonSubTypes` name; a `case` in `ws/GameWebSocketHandler.answer` (the `switch` is exhaustive); the work itself in `session/GameSessionService` or `PlayerConnections`, never in `ws`.
3. Server to client: a record in `protocol/ServerMessage`. If it reports a change of the game, it is not a new message: it is an `update`, through a save and the outbox.
4. Tests: the service level first, then `ws/GameWebSocketTest`; on the client, `game/game-socket.service.spec.ts`.

## 5. Add a bot

1. The bot in core's `bot` package, implementing `Bot` (its only memory is its generator: `rngState()` and a constructor taking it back).
2. `session/BotRoster`: register its name. `GET /api/bots` lists it, and the home page offers it.
3. A test that a bot game plays to the end through the service (`GameSessionServiceTest` style).

## 6. Add a storage adapter (the shared store)

Phase 8 (`docs/design.md` §8.2). Neither the service nor the connections change.

1. `GameRepositoryDatabase implements GameRepository`: store `GameJson.writeValue(session)` with the version in its own column; `save` is an `UPDATE … WHERE id = ? AND version = ?` that throws `VersionConflict` when no row matched. Use the store's own expiry instead of `evictExpired`.
2. `GameUpdatesDatabase implements GameUpdates`: for example Postgres `NOTIFY game, version` and `LISTEN`. Notifications stay tiny (8 KB cap): never put a message in them.
3. Wire them in `config/SessionConfig` instead of the in-memory ones, behind a profile.
4. Run `TwoInstancesTest`'s scenarios against them.

## 7. Change the client

- Standalone components, signals, `HttpClient`, RxJS `webSocket()`, plain CSS; no UI or state library.
- The client computes no rule: it renders `GameView`, the log, prompts and labels, and filters the decision's actions (`DecisionGroups`). If you are about to compute a cost, a legality or a damage, add it to the server's view instead.
- Pages are thin: logic that can be tested without a DOM goes in a plain class (like `DecisionGroups`) with its spec.
- Tests: Vitest through `ng test`. The WebSocket is injected (`WEB_SOCKET`), so `testing/fake-web-socket.ts` drives it; `testing/views.ts` builds sample views.
- A `<select>` whose options come from an `@for`: bind `[selected]` on each option, not `[value]` on the select, or the value is set before the options exist.
