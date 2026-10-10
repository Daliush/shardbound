package fr.daliush.shardbound.dataset;

import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.dataset.card.CardQuestion;
import fr.daliush.shardbound.dataset.card.CardQuestions;
import fr.daliush.shardbound.dataset.deck.DeckQuestion;
import fr.daliush.shardbound.dataset.deck.DeckQuestions;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import fr.daliush.shardbound.dataset.rule.RuleQuestion;
import fr.daliush.shardbound.dataset.rule.RuleQuestions;
import fr.daliush.shardbound.dataset.unanswerable.UnanswerableQuestion;
import fr.daliush.shardbound.dataset.unanswerable.UnanswerableQuestions;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Every question of a dataset version, one list per file: the recipes' scenarios, the factual and deck-building
 * questions, computed from the engine and the card data, and the simple-rule and unanswerable questions, written once
 * and checked against the current rulebook and cards. Built together, so their ids are checked unique across the
 * files and every rule they cite is checked against the rulebook.
 */
public record Dataset(List<DatasetEntry> scenarios, List<CardQuestion> cards, List<DeckQuestion> decks,
        List<RuleQuestion> rules, List<UnanswerableQuestion> unanswerable) {

    /** The mutated decks are drawn with this seed, so a dataset version always asks about the same decks. */
    public static final long DECK_SEED = 1L;

    public Dataset {
        scenarios = List.copyOf(scenarios);
        cards = List.copyOf(cards);
        decks = List.copyOf(decks);
        rules = List.copyOf(rules);
        unanswerable = List.copyOf(unanswerable);
    }

    public static Dataset build(Content content, Rulebook rulebook, List<Recipe> recipes) {
        Dataset dataset = new Dataset(
                new DatasetBuilder(new GameEngine(content.catalog()), content.catalog(), rulebook).entries(recipes),
                new CardQuestions(content.catalog(), content.textTemplates()).all(),
                new DeckQuestions(content.catalog(), DECK_SEED).all(content.decks().values()),
                new RuleQuestions(rulebook).all(),
                new UnanswerableQuestions(content.catalog(), rulebook).all());
        dataset.check(rulebook);
        return dataset;
    }

    void check(Rulebook rulebook) {
        Set<String> ids = new HashSet<>();
        Stream.of(scenarios.stream().map(DatasetEntry::id), cards.stream().map(CardQuestion::id),
                        decks.stream().map(DeckQuestion::id), rules.stream().map(RuleQuestion::id),
                        unanswerable.stream().map(UnanswerableQuestion::id))
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
