# Game server recipes

Step-by-step changes. Server paths are relative to `engine/api/src/main/java/fr/daliush/shardbound/api/`, its tests to `engine/api/src/test/java/fr/daliush/shardbound/api/`, client paths to `frontend/src/app/`.

## Contents

1. Add a REST endpoint
2. Add a field to a view
3. A new engine event or action
4. Add a protocol message
5. Add a bot
6. Add a DAO implementation (the shared store)
7. Change the client

## 1. Add a REST endpoint

1. Spec §12 first: path, request, response, errors.
2. A method on `controller/rest/ContentController` (read-only content) or `controller/rest/GameController` (games), or a new controller for a new resource. Its request and response records go in `controller/rest/dto/`, with Jakarta validation on requests and `@JsonInclude(NON_NULL)` on responses; their mapping to the domain in `controller/mappers/rest/`.
3. The work itself is a domain service method. The controller calls only the domain.
4. Errors: throw a `domain/bo/error/GameException` subtype; `ApiExceptionHandler` maps it to Problem Details. A new kind of refusal is a new subtype, and the compiler asks for its status in the handler's `switch`.
5. Test in `controller/rest/RestApiTest` with MockMvc: status, body, Problem Details. Client: a method on `api/api-client.ts` and its types in `api/protocol.ts`.

## 2. Add a field to a view

1. Is it the engine's? A number or a sentence the client needs must come from core: add it there first (`shardbound-engine-dev`), next to `Costs`, `AttackDamage` or the describers. If it needs the full state (like labels), compute it in `domain/mappers/view/SeatSnapshotMapper` and keep it in the `SeatSnapshot`, so the outbox carries it.
2. Spec §13.3: the field and its type. `T | null` is always written; `field?` is left out when absent (say so in `ProtocolJson`'s mix-ins).
3. The record in `domain/bo/view/` and its mapper in `domain/mappers/view/`. Read it from the seat's `PlayerView`, never from another player's state.
4. `domain/mappers/view/ViewMappersTest`, then the type in `api/protocol.ts`, then render it.

## 3. A new engine event or action

- **Event**: nothing to do. `domain/mappers/view/EventViewMapper` writes every event from its record components. If a component has a type its `wire` method does not know, the random games of `ViewMappersTest` fail: add a case there.
- **Action type**: a case in `domain/mappers/view/DecisionViewMapper` and a factory in `domain/bo/view/ActionView`; the `type` string in spec §13.3 and in `api/protocol.ts`; in `game/decision-groups.ts`, its source in `sourceOf` if a card or unit performs it.
- **Decision kind**: its prompt in core's `DecisionDescriber`, and its string in `api/protocol.ts`.

## 4. Add a protocol message

1. Spec §13.2.
2. Client to server: a record in `controller/ws/message/ClientMessage` and its `@JsonSubTypes` name; a `case` in `controller/ws/GameWebSocketHandler.answer` (the `switch` is exhaustive); the work itself in a domain service, never in the controller.
3. Server to client: a record in `controller/ws/message/ServerMessage` and a method on `controller/mappers/ws/ServerMessageMapper`. If it reports a change of the game, it is not a new message: it is an `update`, through `GameSaver` and the outbox.
4. Tests: the domain service first, then `controller/ws/GameWebSocketTest`; on the client, `game/game-socket.service.spec.ts`.

## 5. Add a bot

1. The bot in core's `bot` package, implementing `Bot` (its only memory is its generator: `rngState()` and a constructor taking it back). A bot that looks ahead also needs what to simulate with, such as the `GameEngine`: it is passed to the constructor, never stored with the seat.
2. `domain/services/bot/BotRoster`: register its name, with a factory from the seat's generator state (a bot that needs the engine gets it from the roster, which Spring gives it). `GET /api/bots` lists it, and the home page offers it.
3. A test that a bot game plays to the end through `GamePlayService` (`GamePlayServiceTest` style).

## 6. Add a DAO implementation (the shared store)

Phase 8 (`docs/design.md` §8.2). Neither the domain nor the adapters change.

1. `dao/game/GameDaoDatabase implements GameDao`: a row per game with `id`, `version`, `status`, `last_activity` and the `GameEntity` as JSON (`GameJson.writeValue`); `save` is an `UPDATE … WHERE id = ? AND version = ?` that throws `VersionConflict` when no row matched. Use the store's own expiry instead of `evictExpired`.
2. `dao/notification/GameNotificationDaoDatabase implements GameNotificationDao`: for example Postgres `NOTIFY game, version` and a thread that `LISTEN`s and calls this instance's listeners. Notifications stay tiny (8 KB cap): never put a message in them. Catch and log each listener's failure, as the in-memory one does.
3. Select them instead of the in-memory ones, behind a profile, in `config/`.
4. Run `controller/ws/TwoInstancesTest`'s scenarios against them.

## 7. Change the client

- Standalone components, signals, `HttpClient`, RxJS `webSocket()`, plain CSS; no UI or state library.
- The client computes no rule: it renders `GameView`, the log, prompts and labels, and filters the decision's actions (`DecisionGroups`). If you are about to compute a cost, a legality or a damage, add it to the server's view instead.
- Pages are thin: logic that can be tested without a DOM goes in a plain class (like `DecisionGroups`) with its spec.
- Tests: Vitest through `ng test`. The WebSocket is injected (`WEB_SOCKET`), so `testing/fake-web-socket.ts` drives it; `testing/views.ts` builds sample views.
- A `<select>` whose options come from an `@for`: bind `[selected]` on each option, not `[value]` on the select, or the value is set before the options exist.
