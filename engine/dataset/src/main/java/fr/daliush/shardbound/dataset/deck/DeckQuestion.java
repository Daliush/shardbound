package fr.daliush.shardbound.dataset.deck;

import java.util.List;

/**
 * One line of {@code decks.jsonl}: "is this deck legal?" about a starter deck or one of its mutations, answered by
 * the engine's {@code DeckValidator}. {@code mutation} decides the split. The question shows the deck in
 * {@code decklist}, written by code and never reworded, so a paraphrase cannot change the deck it asks about.
 * {@code rules} are the rules the deck breaks, or every deck-building rule for a legal deck.
 */
public record DeckQuestion(String id, String category, String mutation, String question, String decklist,
        List<String> cards, List<String> keyFacts, List<String> rules) {

    public DeckQuestion {
        cards = List.copyOf(cards);
        keyFacts = List.copyOf(keyFacts);
        rules = List.copyOf(rules);
    }
}
