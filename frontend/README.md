# Shardbound test client

A minimal Angular client to play Shardbound against a bot or another human (`specs/phase-2-engine.md` §14). It renders what the game server sends and computes no rule: views, events, prompts and button labels all come from the engine.

```bash
npm start      # http://localhost:4200, proxies /api and /ws to the server on http://localhost:8080
npm test       # unit tests, once (npm run test:watch to keep them running)
npm run build
```

Start the server first, from `engine/`: `./mvnw -pl api -am spring-boot:run`.

- `api/`: the server's contract in TypeScript (`protocol.ts`), the REST client, and the seat tokens kept in `localStorage`.
- `game/`: the game screen. `GameSocketService` holds the connection: it applies updates in version order, about 400 ms apart so a bot's moves can be followed, asks for the full state on a gap, and reconnects. `DecisionGroups` groups a decision's actions by what the player clicks; a choice of one card or unit (a discard, a sacrifice) is clicked on that card or unit. Card texts come from `GET /api/cards`, written by the engine: a card in hand and a relic show all their lines, a unit only its keywords and abilities, its attacks' lines being their tooltips.
- `home/`, `join/`: creating a game, and joining one from an invite link.

Seat tokens are shared by every tab of a browser. To play both seats on one machine, use a second browser, a private window, or a second dev server: `npm start -- --port 4201`.

## Benchmark Lab and appearance

The `/lab` route has four primary views: a sortable leaderboard, a chart across all metrics, a two-combination comparison, and a pipeline builder. All scores come from the selected recorded evaluation through `/api/evaluations/`; the browser never runs a model or estimates scores for an unrecorded combination. The random retriever is displayed as a separate reference floor. The builder discovers available layers and variants from the report, preserves its recipe in the URL, and reveals only an exact matching run.

Dataset, split, report, metric, comparison and builder selection are shareable in the URL. The report is fetched only when dataset/split/report changes. Question inspection, category results and history remain accessible under **About this benchmark**.

Each question shows its wording and answer key, read from `/api/datasets/` and joined to the report by id; without the dataset the lab still works on ids. A question opens on the pipeline flow: every stage (BM25, dense, RRF, RRF + refs, in `lab/pipeline-model.ts`; the reranker will be one more) draws its chunks by rank, links each cited chunk from stage to stage with d3, and tables the cited ranks. The metrics are dot plots on an axis zoomed on their values, the random floor as a reference line. Beside the recall curve, the pipelines are ranked by their recall at the chosen budget, and when the budget changes each row slides to its new rank (unless motion is reduced). By category, one budget slider and one legend drive every category's recall chart. The four main pipelines have their own color; the BM25 variants share a muted shade of BM25's.

Appearance is controlled by the sun/moon button (including in the arena), or **Settings → Appearance** for the system preference. The preference is saved locally and applied before first paint. `core/themes.css` contains both application and game semantic colors. The light palette uses a warm stone base (`#e6e3dc`), soft ivory panels, and charcoal text across the home, Lab, and game surfaces; browser chrome uses the same base from first paint. Lab animations use GSAP, stop on component destruction, and respect reduced motion and instant animation preferences.

## Elemental home

The home presents three real catalog cards floating above a shared cinematic landscape, with elemental light and animated filaments around each card. No card is selected initially: Ember is the fallback until the first hover or focus, then the last preview remains active. Clicking commits a starter; after later previews the atmosphere returns to that selection. Arrow keys, Home and End also select a faction, and entering the Arena preserves the current starter in the URL. At widths up to 800px, a stepped carousel shows one large centered card and smaller previews on both sides, retaining the desktop levitation and auras. A horizontal swipe advances exactly one faction on release, wraps in both directions, and settles before accepting another step; vertical scrolling and pinch zoom stay native. Side-card taps, previous/next buttons and arrow keys also change the main card and starter together. The two entry links retain native modified-click behavior and otherwise finish a short transition before routing. Card inspection uses the real catalog; loading and catalog failures have explicit states.

On a fine pointer above 800px, a lazy Three.js scene raycasts the cursor onto the card plane and creates translucent heat ribbons with sparse embers, short grass blades, or soft water reflections and wave crests. Grass retracts within 85ms after leaving the card or moving more than 24px away; other trails fade within 220ms. There is one transparent renderer, targeting 60 frames per second, with 33 instances maximum and capped pixel density. Rendering stops at rest, on scroll, on window blur and when hidden. Resize, motion preferences, context loss and route destruction clear or release the scene. Card and aura loops pause when the home is offscreen or hidden. Touch keeps CSS levitation and auras without the cursor scene. Reduced motion and instant animation use static cards and immediate carousel steps. Unavailable WebGL keeps functional selection, auras and the landscape backdrop.

The generated landscapes and their exact generation prompts live in `public/portals/`; each WebP has an adjacent provenance JSON sidecar. They depict cinematic landscapes rather than named real locations. The shared dark palette starts at slate `#252d3a`, including the first-paint metadata and standalone application manifest. Backdrops fade into the current theme's base color at the edges.
