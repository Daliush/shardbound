package fr.daliush.shardbound.dataset.deck;

import java.util.Optional;

/** A kind of change to a starter deck, and the one deck-building rule it breaks; a deck question's split group. */
enum Mutation {
    LEGAL("legal", Optional.empty()),
    CARD_COUNT("card-count", Optional.of("2.1")),
    FACTION("faction", Optional.of("2.2")),
    COPIES("copies", Optional.of("2.3")),
    TOKEN("token", Optional.of("2.4"));

    private final String label;
    private final Optional<String> breaks;

    Mutation(String label, Optional<String> breaks) {
        this.label = label;
        this.breaks = breaks;
    }

    String label() {
        return label;
    }

    Optional<String> breaks() {
        return breaks;
    }
}
