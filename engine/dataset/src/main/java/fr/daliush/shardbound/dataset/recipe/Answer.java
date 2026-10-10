package fr.daliush.shardbound.dataset.recipe;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.state.GameState;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/** What answers a scenario's question, once its choices are played. */
public sealed interface Answer {

    /**
     * "What happens?": the events of the outcome that answer, whose sentences become the key facts. {@code finalState}
     * adds facts the events do not spell out, such as a unit's defense once a bonus ends: the engine computes the
     * values, the recipe words them.
     */
    record Events(Predicate<GameEvent> select, Function<GameState, List<String>> finalState) implements Answer {

        public Events(Predicate<GameEvent> select) {
            this(select, state -> List.of());
        }
    }

    /**
     * "Can I…?": whether {@code move} is one of the legal actions of the decision the game waits for. The engine says
     * which; the key fact is {@code can} or {@code cannot}, and the rule that decides it is a context rule.
     */
    record Legality(BiPredicate<GameState, Action> move, String can, String cannot) implements Answer {}
}
