package fr.daliush.shardbound.core.testing;

import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.scenario.Choice;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.scenario.ScenarioResult;
import fr.daliush.shardbound.core.scenario.ScenarioRunner;
import fr.daliush.shardbound.core.state.GameState;
import java.util.List;

/** Shortcuts for rule tests: build a scenario, run it, read its trace. Meant for static import. */
public final class RuleTesting {

    public static final GameEngine ENGINE = new GameEngine(TestCards.CATALOG);

    private RuleTesting() {
    }

    public static ScenarioBuilder scenario() {
        return ScenarioBuilder.of(TestCards.CATALOG);
    }

    public static ScenarioResult run(GameState start, Choice... choices) {
        return new ScenarioRunner(ENGINE).run(start, choices);
    }

    /** The trace as "EventType[rule, rule]" lines, to check order and rule IDs together. */
    public static List<String> trace(ScenarioResult result) {
        return result.events().stream().map(RuleTesting::line).toList();
    }

    private static String line(GameEvent event) {
        return event.getClass().getSimpleName() + event.rules();
    }
}
