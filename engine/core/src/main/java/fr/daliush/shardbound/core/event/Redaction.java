package fr.daliush.shardbound.core.event;

import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;

/** Hides from a player what they may not see (3.7). */
public final class Redaction {

    private Redaction() {
    }

    public static List<GameEvent> forViewer(List<GameEvent> events, PlayerId viewer) {
        return events.stream().map(event -> event.seenBy(viewer)).toList();
    }
}
