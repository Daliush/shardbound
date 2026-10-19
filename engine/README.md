# engine

The Java side of Shardbound: Java 21, one Maven build with four modules.

| Module | What it does |
|---|---|
| `core` | The rules engine. It plays a game by the rulebook. A library, no server ([README](core/README.md)). |
| `api` | The game server. It runs the games for the browser, over REST and WebSocket. |
| `dataset` | Writes the Arbiter's evaluation questions and their answers ([README](dataset/README.md)). |
| `corpus` | Cuts the rulebook and the cards into chunks for the Arbiter's search ([README](corpus/README.md)). |

## Run

```bash
./mvnw verify                        # build and test everything
./mvnw -pl api -am spring-boot:run   # the game server, on http://localhost:8080
```

## The game server's environment variables

None is needed on your machine.

| Variable | Needed | What |
|---|---|---|
| `SHARDBOUND_WEBSOCKET_ALLOWEDORIGINS` | On Cloud Run | The client's URL, e.g. `https://shardbound-web-123456789.europe-west1.run.app`. Only these sites can open a game. Default: `localhost` only. |
| `PORT` | No, Cloud Run sets it | The port to listen on. Default: `8080`. |
| `SHARDBOUND_CONTENT_DIR` | No, the image sets it | The cards and decks (`content/`). Without it, the server looks for `content/` in the parent folders. |
| `SHARDBOUND_REPORTS_DIR`, `SHARDBOUND_DATASETS_DIR` | No, the image sets them | The saved evaluations and their datasets (`evals/`). |

## Google Cloud Run

`<registry>` is your Artifact Registry repository, e.g. `europe-west1-docker.pkg.dev/<project>/shardbound`. From the repository root:

```bash
docker build -f engine/api/Dockerfile -t <registry>/shardbound-api .
docker push <registry>/shardbound-api
gcloud run deploy shardbound-api --image <registry>/shardbound-api --allow-unauthenticated \
  --max-instances 1 --timeout 3600 --memory 1Gi
```

- `--max-instances 1`: the games live in memory, in one instance. They are lost when it stops.
- `--timeout 3600`: a game's WebSocket stays open up to one hour. The client reconnects after that.

Then deploy the client ([frontend/README.md](../frontend/README.md)) and give its URL to the server:

```bash
gcloud run services update shardbound-api \
  --update-env-vars SHARDBOUND_WEBSOCKET_ALLOWEDORIGINS=<client url>
```
