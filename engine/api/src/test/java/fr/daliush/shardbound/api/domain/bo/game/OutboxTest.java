package fr.daliush.shardbound.api.domain.bo.game;

import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OutboxTest {

    @Test
    void givesASeatItsUpdatesInOrderAfterTheLastOneSent() {
        Outbox outbox = Outbox.empty()
                .with(1, List.of(update(1, P1), update(1, P2)))
                .with(2, List.of(update(2, P1), update(2, P2)))
                .with(3, List.of(update(3, P1)));

        assertThat(versions(outbox.after(1, 3, P1))).hasValue(List.of(2, 3));
        assertThat(versions(outbox.after(0, 2, P2))).hasValue(List.of(1, 2));
        assertThat(versions(outbox.after(3, 3, P1))).hasValue(List.of());
    }

    @Test
    void keepsOnlyTheLastVersionsSoASeatTooFarBehindGetsTheState() {
        Outbox outbox = Outbox.empty();
        int last = Outbox.KEPT_VERSIONS + 10;
        for (int version = 1; version <= last; version++) {
            outbox = outbox.with(version, List.of(update(version, P1)));
        }

        assertThat(outbox.updates()).hasSize(Outbox.KEPT_VERSIONS);
        assertThat(outbox.after(0, last, P1)).isEmpty();
        assertThat(versions(outbox.after(last - 2, last, P1))).hasValue(List.of(last - 1, last));
    }

    private static SeatUpdate update(int version, PlayerId seat) {
        return new SeatUpdate(version, seat, new SeatSnapshot(null, List.of(), Optional.empty()), List.of());
    }

    private static Optional<List<Integer>> versions(Optional<List<SeatUpdate>> updates) {
        return updates.map(list -> list.stream().map(SeatUpdate::version).toList());
    }
}
