package fr.daliush.shardbound.api.domain.services.update;

import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.game.GameSession;
import fr.daliush.shardbound.api.domain.bo.view.StateView;
import fr.daliush.shardbound.api.domain.bo.view.UpdateView;
import fr.daliush.shardbound.api.testing.TestInstance;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.random.SplitMix64;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SeatUpdateServiceTest {

    private final TestInstance server = TestInstance.alone();

    @Test
    void theStateIsTheFullViewAndTheWholeHistoryRedactedForTheSeat() {
        GameId id = server.creation.create(TestInstance.botGame(11)).game();
        GameSession session = server.session(id);

        StateView state = server.seatUpdates.state(id, P1);

        assertThat(state.version()).isEqualTo(session.version());
        assertThat(state.history()).hasSize(session.events().size());
        assertThat(state.view().opponent().orElseThrow().handCount()).isPositive();
        assertThat(state.history()).filteredOn(event -> event.type().equals("card_drawn")
                        && event.fields().get("player").equals("opponent"))
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.fields().get("card")).isNull());
    }

    @Test
    void aWaitingGameShowsOnlyTheCreatorsDeck() {
        GameId id = server.creation.create(TestInstance.humanGame(5)).game();

        StateView state = server.seatUpdates.state(id, P1);

        assertThat(state.view().status()).isEqualTo("waiting_for_opponent");
        assertThat(state.view().opponent()).isEmpty();
        assertThat(state.view().you().deckCount()).isEqualTo(30);
    }

    @Test
    void givesTheUpdatesASeatMissedInOrder() {
        GameId id = server.creation.create(TestInstance.botGame(7)).game();
        int start = server.session(id).version();
        SplitMix64 clicks = new SplitMix64(2);
        for (int i = 0; i < 5; i++) {
            Decision decision = server.decision(id).orElseThrow();
            server.play.act(id, P1, decision.id(), TestInstance.anyIndex(decision, clicks));
        }
        int now = server.session(id).version();

        List<UpdateView> missed = server.seatUpdates.since(id, P1, start).orElseThrow();

        assertThat(missed).extracting(UpdateView::version)
                .isEqualTo(IntStream.rangeClosed(start + 1, now).boxed().toList());
        assertThat(server.seatUpdates.since(id, P1, now)).hasValue(List.of());
    }
}
