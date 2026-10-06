package fr.daliush.shardbound.api.domain.bo.game;

import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;

/** What one seat may see after one save: its snapshot, and that save's events, already redacted for it. */
public record SeatUpdate(int version, PlayerId seat, SeatSnapshot snapshot, List<GameEvent> events) {

    public SeatUpdate {
        events = List.copyOf(events);
    }
}
