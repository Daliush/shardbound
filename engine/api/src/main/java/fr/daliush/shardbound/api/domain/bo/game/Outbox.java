package fr.daliush.shardbound.api.domain.bo.game;

import fr.daliush.shardbound.core.state.PlayerId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The updates of the last saves, per human seat, kept with the game (spec §13.4). Whichever instance holds a
 * seat's connection sends them from here, so a lost notification costs nothing.
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

    /** Adds the updates of {@code version}, and forgets the versions no longer kept. */
    public Outbox with(int version, List<SeatUpdate> added) {
        List<SeatUpdate> kept = new ArrayList<>(updates.stream()
                .filter(update -> update.version() > version - KEPT_VERSIONS)
                .toList());
        kept.addAll(added);
        return new Outbox(kept);
    }

    /** The seat's updates from {@code sent + 1} to {@code current}, in order; empty if some are no longer kept. */
    public Optional<List<SeatUpdate>> after(int sent, int current, PlayerId seat) {
        List<SeatUpdate> missing = updates.stream()
                .filter(update -> update.seat() == seat && update.version() > sent && update.version() <= current)
                .toList();
        return missing.size() == current - sent ? Optional.of(missing) : Optional.empty();
    }
}
