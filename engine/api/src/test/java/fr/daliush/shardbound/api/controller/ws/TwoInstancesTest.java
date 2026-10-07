package fr.daliush.shardbound.api.controller.ws;

import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.api.dao.game.GameDao;
import fr.daliush.shardbound.api.dao.game.GameDaoInMemory;
import fr.daliush.shardbound.api.dao.notification.GameNotificationDao;
import fr.daliush.shardbound.api.dao.notification.GameNotificationDaoInMemory;
import fr.daliush.shardbound.api.domain.bo.command.Rejection.Reason;
import fr.daliush.shardbound.api.domain.bo.command.Rejection;
import fr.daliush.shardbound.api.domain.bo.command.SeatAccess;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.game.GameStatus;
import fr.daliush.shardbound.api.testing.FakeWebSocketSession;
import fr.daliush.shardbound.api.testing.TestInstance;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.random.SplitMix64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Two instances of the server sharing one game DAO and one notification DAO, as on Cloud Run (spec §13.4). */
class TwoInstancesTest {

    private final GameDao games = new GameDaoInMemory();
    private final GameNotificationDao notifications = new GameNotificationDaoInMemory();
    private final TestInstance a = new TestInstance(games, notifications, "a");
    private final TestInstance b = new TestInstance(games, notifications, "b");

    @Test
    void humansConnectedToDifferentInstancesPlayEachOtherAndReceiveEveryUpdateInOrder() {
        SeatAccess creator = a.creation.create(TestInstance.humanGame(5));
        GameId id = creator.game();
        FakeWebSocketSession onA = new FakeWebSocketSession();
        a.sockets.open(a.socket(id, P1, onA));
        b.creation.join(id, creator.joinCode().orElseThrow(), "root-starter");
        FakeWebSocketSession onB = new FakeWebSocketSession();
        b.sockets.open(b.socket(id, P2, onB));

        SplitMix64 players = new SplitMix64(9);
        int intercepts = 0;
        for (Optional<Decision> decision = a.decision(id); decision.isPresent(); decision = a.decision(id)) {
            TestInstance instance = decision.get().player() == P1 ? a : b;
            if (decision.get().kind() == DecisionKind.INTERCEPT) {
                intercepts++;
            }
            int index = TestInstance.anyIndex(decision.get(), players);
            assertThat(instance.play.act(id, decision.get().player(), decision.get().id(), index)).isEmpty();
        }

        int last = a.session(id).version();
        assertThat(intercepts).as("intercepts answered on the defender's instance, mid-turn").isPositive();
        assertThat(onA.versions()).isEqualTo(IntStream.rangeClosed(0, last).boxed().toList());
        assertThat(onB.versions()).isEqualTo(IntStream.rangeClosed(1, last).boxed().toList());
        assertThat(onA.types().getFirst()).isEqualTo("state");
        assertThat(onA.types().subList(1, onA.types().size())).containsOnly("update");
    }

    @Test
    void theSameAnswerSentToBothInstancesAtOnceIsAppliedOnce() throws Exception {
        GameId id = a.creation.create(TestInstance.botGame(21)).game();
        Decision decision = a.decision(id).orElseThrow();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Optional<Rejection>>> answers = List.of(a, b).stream()
                    .map(instance -> CompletableFuture.supplyAsync(() -> {
                        await(start);
                        return instance.play.act(id, P1, decision.id(), 0);
                    }, threads))
                    .toList();
            start.countDown();

            List<Optional<Reason>> reasons = answers.stream().map(CompletableFuture::join)
                    .map(rejection -> rejection.map(Rejection::reason)).toList();
            assertThat(reasons).containsExactlyInAnyOrder(Optional.empty(), Optional.of(Reason.STALE_DECISION));
        }
        assertThat(a.session(id).actions()).first().isEqualTo(decision.actions().getFirst());
    }

    @Test
    void aBotGameWhoseMessagesAlternateBetweenInstancesPlaysToTheEnd() {
        GameId id = a.creation.create(TestInstance.botGame(4)).game();
        FakeWebSocketSession human = new FakeWebSocketSession();
        b.sockets.open(b.socket(id, P1, human));

        SplitMix64 clicks = new SplitMix64(1);
        int turn = 0;
        for (Optional<Decision> decision = a.decision(id); decision.isPresent(); decision = a.decision(id)) {
            TestInstance instance = turn++ % 2 == 0 ? a : b;
            assertThat(instance.play.act(id, P1, decision.get().id(),
                    TestInstance.anyIndex(decision.get(), clicks))).isEmpty();
        }

        int last = a.session(id).version();
        assertThat(a.session(id).status()).isEqualTo(GameStatus.FINISHED);
        assertThat(human.versions().getLast()).isEqualTo(last);
        assertThat(human.versions()).isSorted().doesNotHaveDuplicates();
    }

    @Test
    void aNewSocketForASeatClosesTheOlderOneOnTheOtherInstance() {
        GameId id = a.creation.create(TestInstance.botGame(2)).game();
        FakeWebSocketSession first = new FakeWebSocketSession();
        FakeWebSocketSession second = new FakeWebSocketSession();

        a.sockets.open(a.socket(id, P1, first));
        b.sockets.open(b.socket(id, P1, second));

        assertThat(first.closeStatus().getCode()).isEqualTo(4409);
        assertThat(second.closeStatus()).isNull();
        int before = first.received().size();
        b.play.act(id, P1, a.decision(id).orElseThrow().id(), 0);
        assertThat(first.received()).hasSize(before);
        assertThat(second.types()).contains("update");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
