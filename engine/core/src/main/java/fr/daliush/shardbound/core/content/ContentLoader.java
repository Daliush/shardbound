package fr.daliush.shardbound.core.content;

import fr.daliush.shardbound.core.content.json.CardParser;
import fr.daliush.shardbound.core.content.json.DeckParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Loads the repository's {@code content/} folder: {@code cards/<faction>/*.json} and {@code decks/*.json}. */
public final class ContentLoader {

    private static final String SCHEMA_SUFFIX = ".schema.json";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final CardParser cardParser = new CardParser();
    private final DeckParser deckParser = new DeckParser();

    public static Content load(Path contentDir) {
        return new ContentLoader().read(contentDir);
    }

    private Content read(Path contentDir) {
        CardCatalog catalog = new CardCatalog(jsonFiles(contentDir.resolve("cards"), 2).stream()
                .map(file -> cardParser.parse(readTree(file), file.toString()))
                .toList());
        Map<DeckId, Deck> decks = new LinkedHashMap<>();
        for (Path file : jsonFiles(contentDir.resolve("decks"), 1)) {
            Deck deck = deckParser.parse(readTree(file), file.toString());
            if (decks.put(deck.id(), deck) != null) {
                throw new ContentException("Two decks have the id " + deck.id());
            }
        }
        checkReferences(catalog, decks.values());
        return new Content(catalog, decks);
    }

    private static void checkReferences(CardCatalog catalog, Iterable<Deck> decks) {
        for (CardDefinition card : catalog.all()) {
            card.allEffects().forEach(effect -> {
                if (effect instanceof Effect.Summon summon && !isToken(catalog, summon.token())) {
                    throw new ContentException(card.id() + " summons " + summon.token() + ", which is not a token");
                }
            });
        }
        for (Deck deck : decks) {
            for (DeckEntry entry : deck.cards()) {
                if (catalog.find(entry.card()).isEmpty()) {
                    throw new ContentException("Deck " + deck.id() + " contains " + entry.card()
                            + ", which does not exist");
                }
            }
        }
    }

    private static boolean isToken(CardCatalog catalog, CardId id) {
        return catalog.find(id).map(CardDefinition::isToken).orElse(false);
    }

    private JsonNode readTree(Path file) {
        try {
            return mapper.readTree(file);
        } catch (JacksonException e) {
            throw new ContentException(file + ": invalid JSON", e);
        }
    }

    /** JSON files exactly {@code depth} levels below {@code dir}, schemas excluded, sorted by path. */
    private static List<Path> jsonFiles(Path dir, int depth) {
        if (!Files.isDirectory(dir)) {
            throw new ContentException("Content folder not found: " + dir);
        }
        try (Stream<Path> files = Files.walk(dir, depth)) {
            return files
                    .filter(file -> dir.relativize(file).getNameCount() == depth)
                    .filter(file -> file.toString().endsWith(".json") && !file.toString().endsWith(SCHEMA_SUFFIX))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new ContentException("Cannot read " + dir, e);
        }
    }
}
