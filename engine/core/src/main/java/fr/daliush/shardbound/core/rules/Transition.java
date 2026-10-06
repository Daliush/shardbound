package fr.daliush.shardbound.core.rules;

import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.state.GameState;
import java.util.List;

/** The new state, and the events that describe how the game got there. */
public record Transition(GameState state, List<GameEvent> events) {

    public Transition {
        events = List.copyOf(events);
    }
}
