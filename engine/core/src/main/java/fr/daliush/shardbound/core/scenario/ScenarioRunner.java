package fr.daliush.shardbound.core.scenario;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.Transition;
import fr.daliush.shardbound.core.state.GameState;
import java.util.ArrayList;
import java.util.List;

/** Plays choices from a state and returns the exact outcome with its rule trace (design doc §3.4). */
public final class ScenarioRunner {

    private final GameEngine engine;

    public ScenarioRunner(GameEngine engine) {
        this.engine = engine;
    }

    public ScenarioResult run(GameState start, Choice... choices) {
        List<GameEvent> events = new ArrayList<>();
        List<Decision> decisions = new ArrayList<>();
        Transition transition = engine.resume(start);
        events.addAll(transition.events());
        GameState state = transition.state();
        for (Choice choice : choices) {
            GameState current = state;
            Decision decision = engine.decision(current)
                    .orElseThrow(() -> new IllegalStateException("The game is over; no choice is possible"));
            decisions.add(decision);
            Action action = choice.pick(current, decision);
            transition = engine.apply(current, action);
            events.addAll(transition.events());
            state = transition.state();
        }
        return new ScenarioResult(state, events, decisions);
    }
}
