package fr.daliush.shardbound.core.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    /** Every way the deck breaks the rules, as messages citing their rule; empty if it is legal. */
    public List<String> problems(Deck deck) {
        return check(deck).stream().map(DeckProblem::message).toList();
    }

    /** Every way the deck breaks the rules; empty if it is legal. */
    public List<DeckProblem> check(Deck deck) {
        List<DeckProblem> problems = new ArrayList<>();
        if (deck.size() != DECK_SIZE) {
            problems.add(new DeckProblem.WrongSize(deck.size()));
        }
        Map<CardId, Integer> entries = new LinkedHashMap<>();
        Map<CardId, Integer> copies = new LinkedHashMap<>();
        for (DeckEntry entry : deck.cards()) {
            entries.merge(entry.card(), 1, Integer::sum);
            copies.merge(entry.card(), entry.count(), Integer::sum);
        }
        entries.forEach((card, times) -> {
            if (times > 1) {
                problems.add(new DeckProblem.ListedTwice(card, times));
            }
        });
        copies.forEach((card, count) -> problems.addAll(cardProblems(deck, card, count)));
        return problems;
    }

    private List<DeckProblem> cardProblems(Deck deck, CardId id, int count) {
        Optional<CardDefinition> found = catalog.find(id);
        if (found.isEmpty()) {
            return List.of(new DeckProblem.UnknownCard(id));
        }
        CardDefinition card = found.get();
        List<DeckProblem> problems = new ArrayList<>();
        if (count > MAX_COPIES) {
            problems.add(new DeckProblem.TooManyCopies(id, count));
        }
        if (card.isToken()) {
            problems.add(new DeckProblem.Token(id));
        }
        if (card.faction() != deck.faction() && card.faction() != Faction.NEUTRAL) {
            problems.add(new DeckProblem.OtherFaction(id, card.faction(), deck.faction()));
        }
        return problems;
    }
}
