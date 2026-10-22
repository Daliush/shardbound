# frontend

The Shardbound client, in Angular. You play a game against a bot or a friend, and you explore the Arbiter's benchmarks (`/lab`). It computes no rule: everything comes from the game server.

## Run

Start the game server first ([engine/README.md](../engine/README.md)). Then:

```bash
npm install
npm start       # http://localhost:4200, sends /api and /ws to the server on :8080
npm test        # unit tests, once
npm run build
```

To play both seats on one computer, open the second one in a private window.

## Code (`src/app/`)

| Folder | What |
|---|---|
| `api/` | The server's contract (`protocol.ts`) and the REST client |
| `home/`, `play/`, `join/` | Pick a deck, create a game, join one from an invite link |
| `game/` | The game page and its WebSocket connection |
| `arena/` | The game table: units, hand, combat, animations |
| `cards/` | How a card looks, and the inspected card in 3D |
| `collection/` | Every card, searched, filtered and grouped (`/cards`) |
| `lab/` | The benchmark lab; `metric-catalog.ts` names, writes and explains each metric from the evaluation file, and `metric-guide.ts` is its "Our metrics" page |
| `core/` | Themes, settings, shared styles, and the message shown while the server wakes up (`server-wake.ts`) |

## Docker and Google Cloud Run

The image serves the built client with nginx. nginx also passes `/api` and `/ws` to the game server, so the browser talks to a single address.

| Variable | What |
|---|---|
| `API_URL` | The game server's URL, without a trailing `/`. Required on Cloud Run: without it, the pages load but `/api` answers 502. Default: `http://api:8080` (docker compose). |
| `PORT` | The port to listen on. Cloud Run sets it. Default: `8080`. |

Deploy the game server first, then, with the same `<registry>`:

```bash
docker build -t <registry>/shardbound-web frontend
docker push <registry>/shardbound-web
gcloud run deploy shardbound-web --image <registry>/shardbound-web --allow-unauthenticated \
  --timeout 3600 --set-env-vars API_URL=<server url>
```

Then give this service's URL to the server: `SHARDBOUND_WEBSOCKET_ALLOWEDORIGINS` ([engine/README.md](../engine/README.md)).

The game server keeps no instance running, so the first request after a quiet spell waits for one to start. When requests to the server stay unanswered for more than 3 seconds, the client says so at the top of the page.
