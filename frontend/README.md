# Shardbound test client

A minimal Angular client to play Shardbound against a bot or another human (`specs/phase-2-engine.md` §14). It renders what the game server sends and computes no rule: views, events, prompts and button labels all come from the engine.

```bash
npm start      # http://localhost:4200, proxies /api and /ws to the server on http://localhost:8080
npm test       # unit tests, once (npm run test:watch to keep them running)
npm run build
```

Start the server first, from `engine/`: `./mvnw -pl api -am spring-boot:run`.

- `api/`: the server's contract in TypeScript (`protocol.ts`), the REST client, and the seat tokens kept in `localStorage`.
- `game/`: the game screen. `GameSocketService` holds the connection: it applies updates in version order, about 400 ms apart so a bot's moves can be followed, asks for the full state on a gap, and reconnects. `DecisionGroups` groups a decision's actions by what the player clicks.
- `home/`, `join/`: creating a game, and joining one from an invite link.

Seat tokens are shared by every tab of a browser. To play both seats on one machine, use a second browser, a private window, or a second dev server: `npm start -- --port 4201`.
