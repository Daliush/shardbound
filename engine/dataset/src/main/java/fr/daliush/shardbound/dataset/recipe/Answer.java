package fr.daliush.shardbound.dataset.recipe;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.state.GameState;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** What answers a scenario's question, once its choices are played. */
public sealed interface Answer {

    /** "What happens?": the events of the outcome that answer; their sentences become the key facts. */
    record Events(Predicate<GameEvent> select) implements Answer {}

    /**
     * "Can I…?": whether {@code move} is one of the legal actions of the decision the game waits for. The engine says
     * which; the key fact is {@code can} or {@code cannot}, and the rule that decides it is a context rule.
     */
    record Legality(BiPredicate<GameState, Action> move, String can, String cannot) implements Answer {}
}
