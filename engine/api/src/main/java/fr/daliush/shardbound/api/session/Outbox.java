package fr.daliush.shardbound.api.session;

import fr.daliush.shardbound.core.state.PlayerId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The update messages of the last saves, per seat, saved with the session (spec §13.4). Whichever instance
 * holds a seat's connection sends them from here, so a lost notification costs nothing.
 */
public record Outbox(List<SeatUpdate> updates) {

    /** A connection further behind than this gets the full state instead. */
    public static final int KEPT_VERSIONS = 50;

    public Outbox {
        updates = List.copyOf(updates);
    }

    public static Outbox empty() {
        return new Outbox(List.of());
    }

    public record SeatUpdate(int version, PlayerId seat, String message) {}

    public Outbox with(int version, Map<PlayerId, String> messages) {
        List<SeatUpdate> kept = new ArrayList<>(updates.stream()
                .filter(update -> update.version() > version - KEPT_VERSIONS)
                .toList());
        for (PlayerId seat : PlayerId.values()) {
            if (messages.containsKey(seat)) {
                kept.add(new SeatUpdate(version, seat, messages.get(seat)));
            }
        }
        return new Outbox(kept);
    }

    /** The seat's messages from {@code sent + 1} to {@code current}, in order; empty if some are no longer kept. */
    public Optional<List<String>> after(int sent, int current, PlayerId seat) {
        List<String> messages = updates.stream()
                .filter(update -> update.seat() == seat && update.version() > sent && update.version() <= current)
                .map(SeatUpdate::message)
                .toList();
        return messages.size() == current - sent ? Optional.of(messages) : Optional.empty();
    }
}
