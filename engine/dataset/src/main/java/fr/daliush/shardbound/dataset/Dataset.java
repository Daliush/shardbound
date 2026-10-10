package fr.daliush.shardbound.dataset;

import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.dataset.card.CardQuestion;
import fr.daliush.shardbound.dataset.card.CardQuestions;
import fr.daliush.shardbound.dataset.deck.DeckQuestion;
import fr.daliush.shardbound.dataset.deck.DeckQuestions;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The questions the engine and the card data answer, one list per file: the recipes' scenarios, the factual questions
 * and the deck-building questions. Built together, so their ids are checked unique across the three files and every
 * rule they cite is checked against the rulebook.
 */
public record Dataset(List<DatasetEntry> scenarios, List<CardQuestion> cards, List<DeckQuestion> decks) {

    /** The mutated decks are drawn with this seed, so a dataset version always asks about the same decks. */
    public static final long DECK_SEED = 1L;

    public Dataset {
        scenarios = List.copyOf(scenarios);
        cards = List.copyOf(cards);
        decks = List.copyOf(decks);
    }

    public static Dataset build(Content content, Rulebook rulebook, List<Recipe> recipes) {
        Dataset dataset = new Dataset(
                new DatasetBuilder(new GameEngine(content.catalog()), content.catalog(), rulebook).entries(recipes),
                new CardQuestions(content.catalog(), content.textTemplates()).all(),
                new DeckQuestions(content.catalog(), DECK_SEED).all(content.decks().values()));
        dataset.check(rulebook);
        return dataset;
    }

    void check(Rulebook rulebook) {
        Set<String> ids = new HashSet<>();
        Stream.of(scenarios.stream().map(DatasetEntry::id), cards.stream().map(CardQuestion::id),
                        decks.stream().map(DeckQuestion::id))
                .flatMap(id -> id)
                .filter(id -> !ids.add(id))
                .findFirst()
                .ifPresent(id -> {
                    throw new IllegalStateException("Two questions have the id " + id);
                });
        for (CardQuestion card : cards) {
            if (card.keyFacts().isEmpty()) {
                throw new IllegalStateException(card.id() + ": no key fact answers the question");
            }
        }
        for (DeckQuestion deck : decks) {
            deck.rules().stream().filter(rule -> !rulebook.has(rule)).findFirst().ifPresent(rule -> {
                throw new IllegalStateException(deck.id() + ": rule " + rule + " is not in the rulebook");
            });
        }
    }
}
