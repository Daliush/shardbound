package fr.daliush.shardbound.core.content;

import java.util.Map;

/** The game content loaded from {@code content/}: the card catalog and the decks. */
public record Content(CardCatalog catalog, Map<DeckId, Deck> decks) {

    public Content {
        decks = Map.copyOf(decks);
    }

    public Deck deck(String id) {
        Deck deck = decks.get(new DeckId(id));
        if (deck == null) {
            throw new ContentException("Unknown deck " + id);
        }
        return deck;
    }
}
