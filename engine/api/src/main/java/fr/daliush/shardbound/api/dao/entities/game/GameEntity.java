package fr.daliush.shardbound.api.dao.entities.game;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.state.GameState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One stored game. {@code id}, {@code version}, {@code status} and {@code lastActivity} are what a store reads
 * without opening the rest; {@code state} is null while the game waits for its second player.
 */
public record GameEntity(
        UUID id,
        int version,
        String status,
        Instant lastActivity,
        long seed,
        List<SeatEntity> seats,
        GameState state,
        List<GameEvent> events,
        List<Action> actions,
        List<SeatUpdateEntity> outbox) {

    public GameEntity {
        seats = List.copyOf(seats);
        events = List.copyOf(events);
        actions = List.copyOf(actions);
        outbox = List.copyOf(outbox);
    }
}
