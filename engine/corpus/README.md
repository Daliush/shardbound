# The Arbiter's corpus

The rulebook and the cards cut into chunks for the Arbiter's RAG index (design doc §5.2). The Python index embeds and stores them; this module only writes them, with no model involved.

```bash
# from engine/: writes ai/.corpus/chunks.jsonl
./mvnw -q -pl corpus -am compile exec:java
```

The file is derived from `docs/rules/` and `content/cards/`, so it is rewritten before each indexing and not committed.

## How it is built

`CorpusGenerator` is the entry point; each sub-package is one step, with one public class:

| Step | Package | Entry |
|---|---|---|
| 1. The rulebook's chunks, refs as written | `rulebook` | `RulebookChunker.chunkRulebook` |
| 1. The cards' chunks, refs as written | `card` | `CardChunker.chunkAllCards` |
| 2. Each section ref expanded into its rules | `ref` | `SectionRefs.expandSectionRefs` |
| 3. The JSON Lines file | `chunk` | `ChunkWriter.writeJsonLines`, next to the `Chunk` record |

## A chunk

| Kind | One chunk per | Text |
|---|---|---|
| `rule` | rule of `docs/rules/` (`RuleChunker`) | the rule with its items and examples, in its Markdown, prefixed with the titles of its sections (5.2.1 gives `[5. Game sequence > 5.2 Start of turn (in this order)]`) |
| `term` | row of the vocabulary table (`VocabularyChunker`) | `**Owner**: The player whose deck contained the card.` |
| `card` | card of the catalog (`CardChunker`) | what the card is, then its text as `CardTextRenderer` writes it |

`README.md` and `12-open-points.md` describe the rulebook rather than state rules: they make no chunk.

```json
{"id":"11.3.5","kind":"rule","text":"[11.3 Anchor] At the end of the turn during which its protection ended, a doomed unit whose defense is still 0 is destroyed (5.4.3). Since \"Turn end\" abilities resolve first (5.4.1), an end-of-turn heal can still save it.","refs":["5.4.3","5.4.1"],"source":"docs/rules/11-3-anchor.md","hash":"…"}
{"id":"root.root-sentinel","kind":"card","text":"Root Sentinel: Root unit, costs 2 Shards, 5 defense.\nAnchor.\nRoot Slam (2 Shards): Deal 4 damage to the target.","refs":["11.3.1","11.3.2","11.3.3","11.3.4","11.3.5","11.3.6","8.1"],"source":"content/cards/root/root-sentinel.json","hash":"…"}
```

- **`text`** is the only field embedded, indexed and shown to the model. A rule rarely names its subject ("While protected, it…"), so its headings give it one; a card or a term names its own. The ID stays out of the text: the prompt's tags carry it. The rulebook's Markdown stays in: BM25 ignores it, and its bold tells the model a rule's name, a defined term (**doomed**) or an emphasis ("as printed on the card").
- **`refs`** are the chunks a chunk relies on, attached after retrieval. A rule's are the numbers its text writes (`WrittenRefs`). A card's are read from its data (`CardRefs`): a keyword as its section, each effect and trigger as its rule, a summoned token as its card. A section number stands for all of its rules, so `(11.3)` gives 11.3.1 to 11.3.6. The chunkers keep the refs as written; once every chunk is built, `SectionRefs` turns each section number into its rules. `CorpusGeneratorTest` fails on a ref to nothing. A new keyword, effect or trigger does not compile until `CardRefs` maps it.
- **`hash`** is the SHA-256 of `text`: the index embeds a chunk again only when it changes.
- **`source`** is the file the chunk comes from.

A rule's example belongs to it only when it is indented under it (`  > *Example*: …`); the rulebook's README says so.
