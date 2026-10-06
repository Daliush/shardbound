package fr.daliush.shardbound.api.session;

import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutboxTest {

    @Test
    void givesASeatItsMessagesInOrderAfterTheLastOneSent() {
        Outbox outbox = Outbox.empty()
                .with(1, Map.of(P1, "p1-v1", P2, "p2-v1"))
                .with(2, Map.of(P1, "p1-v2", P2, "p2-v2"))
                .with(3, Map.of(P1, "p1-v3"));

        assertThat(outbox.after(1, 3, P1)).hasValue(List.of("p1-v2", "p1-v3"));
        assertThat(outbox.after(0, 2, P2)).hasValue(List.of("p2-v1", "p2-v2"));
        assertThat(outbox.after(3, 3, P1)).hasValue(List.of());
    }

    @Test
    void keepsOnlyTheLastVersionsSoAConnectionTooFarBehindGetsTheState() {
        Outbox outbox = Outbox.empty();
        for (int version = 1; version <= Outbox.KEPT_VERSIONS + 10; version++) {
            outbox = outbox.with(version, Map.of(P1, "v" + version));
        }

        assertThat(outbox.updates()).hasSize(Outbox.KEPT_VERSIONS);
        assertThat(outbox.after(0, Outbox.KEPT_VERSIONS + 10, P1)).isEmpty();
        assertThat(outbox.after(Outbox.KEPT_VERSIONS + 8, Outbox.KEPT_VERSIONS + 10, P1)).hasValue(
                List.of("v" + (Outbox.KEPT_VERSIONS + 9), "v" + (Outbox.KEPT_VERSIONS + 10)));
    }
}
