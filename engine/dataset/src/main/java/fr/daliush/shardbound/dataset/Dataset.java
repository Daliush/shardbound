package fr.daliush.shardbound.dataset;

import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.dataset.card.CardQuestion;
import fr.daliush.shardbound.dataset.card.CardQuestions;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The questions the engine and the card data answer, one list per file: the recipes' scenarios and the factual
 * questions. Built together, so their ids are checked unique across the files.
 */
public record Dataset(List<DatasetEntry> scenarios, List<CardQuestion> cards) {

    public Dataset {
        scenarios = List.copyOf(scenarios);
        cards = List.copyOf(cards);
    }

    public static Dataset build(Content content, Rulebook rulebook, List<Recipe> recipes) {
        Dataset dataset = new Dataset(
                new DatasetBuilder(new GameEngine(content.catalog()), content.catalog(), rulebook).entries(recipes),
                new CardQuestions(content.catalog(), content.textTemplates()).all());
        dataset.check(rulebook);
        return dataset;
    }

    void check(Rulebook rulebook) {
        Set<String> ids = new HashSet<>();
        Stream.concat(scenarios.stream().map(DatasetEntry::id), cards.stream().map(CardQuestion::id))
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
    }
}
