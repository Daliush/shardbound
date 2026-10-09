package fr.daliush.shardbound.dataset;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.dataset.answer.AnswerKey;
import fr.daliush.shardbound.dataset.answer.AnswerKeys;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import fr.daliush.shardbound.dataset.recipe.Scenario;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plays every scenario of the recipes and checks it before it becomes an entry: a unique id, an answer key, context
 * rules that exist, and a question that names no card its asker cannot see. A failed check is a mistake in a recipe,
 * so it stops the build.
 */
public final class DatasetBuilder {

    private final CardCatalog catalog;
    private final Rulebook rulebook;
    private final AnswerKeys answerKeys;

    public DatasetBuilder(GameEngine engine, CardCatalog catalog, Rulebook rulebook) {
        this.catalog = catalog;
        this.rulebook = rulebook;
        this.answerKeys = new AnswerKeys(engine, catalog);
    }

    public List<DatasetEntry> entries(List<Recipe> recipes) {
        List<DatasetEntry> entries = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Recipe recipe : recipes) {
            for (Scenario scenario : recipe.scenarios(catalog)) {
                if (!ids.add(scenario.id())) {
                    throw new IllegalStateException("Two scenarios have the id " + scenario.id());
                }
                checkContextRules(scenario);
                checkQuestion(scenario);
                entries.add(entry(recipe, scenario, answerKeys.of(scenario)));
            }
        }
        return entries;
    }

    private void checkContextRules(Scenario scenario) {
        for (String rule : scenario.contextRules()) {
            if (!rulebook.has(rule)) {
                throw new IllegalStateException(scenario.id() + ": rule " + rule + " is not in the rulebook");
            }
        }
    }

    private void checkQuestion(Scenario scenario) {
        for (String hidden : ScenarioCards.hiddenNames(scenario.start(), scenario.asker(), catalog)) {
            if (scenario.question().contains(hidden)) {
                throw new IllegalStateException(scenario.id() + ": the question names " + hidden
                        + ", which its asker cannot see");
            }
        }
    }

    /** The context rules a key event already carries are left out: they are in {@code rules}. */
    private static DatasetEntry entry(Recipe recipe, Scenario scenario, AnswerKey key) {
        List<String> contextRules = scenario.contextRules().stream().filter(rule -> !key.rules().contains(rule))
                .toList();
        return new DatasetEntry(scenario.id(), recipe.category().label(), recipe.interaction(), scenario.question(),
                ScenarioCards.all(scenario.start()), key.keyFacts(), key.rules(), contextRules, key.trace());
    }
}
