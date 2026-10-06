package fr.daliush.shardbound.core.content;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public record Deck(DeckId id, String name, Optional<String> description, Faction faction, List<DeckEntry> cards) {

    public Deck {
        cards = List.copyOf(cards);
    }

    public int size() {
        return cards.stream().mapToInt(DeckEntry::count).sum();
    }

    /** The decklist as single cards, in entry order. */
    public List<CardId> cardList() {
        return cards.stream()
                .flatMap(entry -> Collections.nCopies(entry.count(), entry.card()).stream())
                .toList();
    }
}
