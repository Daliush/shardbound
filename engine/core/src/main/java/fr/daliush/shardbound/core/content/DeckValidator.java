package fr.daliush.shardbound.core.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** The deck-building rules (rulebook section 2), with the messages of the Python content tests. */
public final class DeckValidator {

    public static final int DECK_SIZE = 30;
    public static final int MAX_COPIES = 2;

    private final CardCatalog catalog;

    public DeckValidator(CardCatalog catalog) {
        this.catalog = catalog;
    }

    /** Every way the deck breaks the rules; empty if it is legal. */
    public List<String> problems(Deck deck) {
        List<String> problems = new ArrayList<>();
        if (deck.size() != DECK_SIZE) {
            problems.add("has " + deck.size() + " cards, needs exactly " + DECK_SIZE + " (2.1)");
        }
        Map<CardId, Integer> entries = new LinkedHashMap<>();
        Map<CardId, Integer> copies = new LinkedHashMap<>();
        for (DeckEntry entry : deck.cards()) {
            entries.merge(entry.card(), 1, Integer::sum);
            copies.merge(entry.card(), entry.count(), Integer::sum);
        }
        entries.forEach((card, times) -> {
            if (times > 1) {
                problems.add("lists " + card + " " + times + " times; use one entry with a count (2.3)");
            }
        });
        copies.forEach((card, count) -> problems.addAll(cardProblems(deck, card, count)));
        return problems;
    }

    private List<String> cardProblems(Deck deck, CardId id, int count) {
        Optional<CardDefinition> found = catalog.find(id);
        if (found.isEmpty()) {
            return List.of("contains " + id + ", which does not exist");
        }
        CardDefinition card = found.get();
        List<String> problems = new ArrayList<>();
        if (count > MAX_COPIES) {
            problems.add("has " + count + " copies of " + id + ", max " + MAX_COPIES + " (2.3)");
        }
        if (card.isToken()) {
            problems.add("contains the token " + id + "; tokens are never in a deck (2.4)");
        }
        if (card.faction() != deck.faction() && card.faction() != Faction.NEUTRAL) {
            problems.add("contains " + id + ", a " + lower(card.faction()) + " card, in a deck of faction "
                    + lower(deck.faction()) + " (2.2)");
        }
        return problems;
    }

    private static String lower(Faction faction) {
        return faction.name().toLowerCase(Locale.ROOT);
    }
}
