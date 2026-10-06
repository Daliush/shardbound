package fr.daliush.shardbound.api.dao.entities.game;

import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.view.PlayerView;
import java.util.List;

/**
 * A stored outbox entry: what one seat may see after one save. {@code prompt} is null and {@code labels} empty
 * when the seat has no decision; {@code events} are already redacted for the seat.
 */
public record SeatUpdateEntity(
        int version,
        PlayerId seat,
        PlayerView view,
        String prompt,
        List<String> labels,
        List<GameEvent> events) {

    public SeatUpdateEntity {
        labels = List.copyOf(labels);
        events = List.copyOf(events);
    }
}
