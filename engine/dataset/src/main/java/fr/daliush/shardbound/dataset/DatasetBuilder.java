package fr.daliush.shardbound.dataset;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.scenario.ScenarioRunner;
import fr.daliush.shardbound.dataset.answer.AnswerKey;
import fr.daliush.shardbound.dataset.answer.AnswerKeys;
import fr.daliush.shardbound.dataset.recipe.Answer;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import fr.daliush.shardbound.dataset.recipe.Scenario;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plays every scenario of the recipes and checks it before it becomes an entry: a unique id, an interaction that
 * belongs to one recipe, an answer key, context rules that exist, and a question that names no card its asker cannot
 * see. A failed check is a mistake in a recipe, so it stops the build.
 */
public final class DatasetBuilder {

    private final CardCatalog catalog;
    private final Rulebook rulebook;
    private final ScenarioRunner runner;
    private final AnswerKeys answerKeys;

    public DatasetBuilder(GameEngine engine, CardCatalog catalog, Rulebook rulebook) {
        this.catalog = catalog;
        this.rulebook = rulebook;
        this.runner = new ScenarioRunner(engine);
        this.answerKeys = new AnswerKeys(catalog);
    }

    public List<DatasetEntry> entries(List<Recipe> recipes) {
        List<DatasetEntry> entries = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Map<String, Recipe> owners = new HashMap<>();
        for (Recipe recipe : recipes) {
            Recipe owner = owners.putIfAbsent(recipe.interaction(), recipe);
            if (owner != null && owner != recipe) {
                throw new IllegalStateException("Two recipes share the interaction " + recipe.interaction());
            }
            for (Scenario scenario : recipe.scenarios(catalog)) {
                if (!ids.add(scenario.id())) {
                    throw new IllegalStateException("Two scenarios have the id " + scenario.id());
                }
                checkContextRules(scenario);
                ScenarioResult result = runner.run(scenario.start(), scenario.choicesToPlay());
                checkResolved(scenario, result);
                checkQuestion(scenario, result);
                entries.add(entry(recipe, scenario, answerKeys.of(scenario, result)));
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

    /**
     * A "what happens" scenario must play its outcome to the end. Stopping on a decision in the middle of a step (an
     * echo order, a target, cards to discard) would cut the answer key short without a sign: its choice is missing.
     */
    private static void checkResolved(Scenario scenario, ScenarioResult result) {
        if (scenario.answer() instanceof Answer.Events) {
            result.pending().filter(decision -> decision.kind().pausesAStep()).ifPresent(decision -> {
                throw new IllegalStateException(scenario.id() + ": the outcome stops on a " + decision.kind()
                        + " decision of " + decision.player() + "; play its choice in the scenario");
            });
        }
    }

    private void checkQuestion(Scenario scenario, ScenarioResult result) {
        for (String hidden : ScenarioCards.hiddenNames(scenario.start(), result, scenario.asker(), catalog)) {
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
