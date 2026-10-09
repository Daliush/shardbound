package fr.daliush.shardbound.dataset.answer;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.scenario.ScenarioRunner;
import fr.daliush.shardbound.core.text.EventDescriber;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import fr.daliush.shardbound.dataset.recipe.Scenario;
import java.util.List;

/** Plays a scenario with the engine and keeps the events its recipe selects. */
public final class AnswerKeys {

    private final ScenarioRunner runner;
    private final EventDescriber describer;

    public AnswerKeys(GameEngine engine, CardCatalog catalog) {
        this.runner = new ScenarioRunner(engine);
        this.describer = EventDescriber.withoutInstanceIds(catalog);
    }

    /** @throws IllegalStateException if no event of the outcome answers the question: the recipe is wrong */
    public AnswerKey of(Recipe recipe, Scenario scenario) {
        ScenarioResult result = runner.run(scenario.start(), scenario.choicesToPlay());
        List<GameEvent> keyEvents = result.events().stream().filter(recipe::isKey).toList();
        if (keyEvents.isEmpty()) {
            throw new IllegalStateException(scenario.id() + ": no event answers the question in " + result.trace());
        }
        return new AnswerKey(
                keyEvents.stream().map(event -> describer.describe(event, scenario.asker())).distinct().toList(),
                keyEvents.stream().flatMap(event -> event.rules().stream()).distinct().toList(),
                result.trace());
    }
}
