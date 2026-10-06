package fr.daliush.shardbound.core.testing;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.bot.Player;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.GameSetup;
import fr.daliush.shardbound.core.rules.Transition;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/** Plays a whole game between two players, the way a game server would. */
public final class GameDriver {

    private static final int MAX_STEPS = 100_000;

    private GameDriver() {
    }

    public record PlayedGame(GameState finalState, List<GameEvent> events, int steps) {}

    public static PlayedGame play(GameEngine engine, GameSetup setup, Player p1, Player p2) {
        return play(engine, setup, p1, p2, (state, decision) -> {
        });
    }

    /** {@code afterEachStep} sees every state reached, with the decision it waits for. */
    public static PlayedGame play(GameEngine engine, GameSetup setup, Player p1, Player p2,
                                  BiConsumer<GameState, Optional<Decision>> afterEachStep) {
        Transition transition = engine.newGame(setup);
        List<GameEvent> history = new ArrayList<>(transition.events());
        GameState state = transition.state();
        afterEachStep.accept(state, engine.decision(state));
        int steps = 0;
        for (Optional<Decision> decision = engine.decision(state); decision.isPresent();
             decision = engine.decision(state)) {
            if (++steps > MAX_STEPS) {
                throw new IllegalStateException("The game did not end after " + MAX_STEPS + " decisions");
            }
            PlayerId decider = decision.get().player();
            Player player = decider == PlayerId.P1 ? p1 : p2;
            Action action = player.choose(engine.view(state, decider, history), decision.get());
            transition = engine.apply(state, action);
            history.addAll(transition.events());
            state = transition.state();
            afterEachStep.accept(state, engine.decision(state));
        }
        return new PlayedGame(state, history, steps);
    }
}
