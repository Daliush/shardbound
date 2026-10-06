package fr.daliush.shardbound.api.session;

import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.api.session.Rejection.Reason;
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

/** Two instances of the server sharing one repository and one pub/sub, as on Cloud Run (spec §13.4). */
class TwoInstancesTest {

    private final GameRepository repository = new GameRepositoryInMemory();
    private final GameUpdates updates = new GameUpdatesInMemory();
    private final TestServer a = new TestServer(repository, updates, "a");
    private final TestServer b = new TestServer(repository, updates, "b");

    @Test
    void humansConnectedToDifferentInstancesPlayEachOtherAndReceiveEveryUpdateInOrder() {
        SeatAccess creator = a.sessions.create(GameSessionServiceTest.humanGame());
        GameId id = creator.game();
        RecordingConnection onA = new RecordingConnection();
        a.connections.open(id, P1, onA);
        b.sessions.join(id, creator.joinCode().orElseThrow(), "root-starter");
        RecordingConnection onB = new RecordingConnection();
        b.connections.open(id, P2, onB);

        SplitMix64 players = new SplitMix64(9);
        int intercepts = 0;
        for (Optional<Decision> decision = a.decision(id); decision.isPresent(); decision = a.decision(id)) {
            TestServer instance = decision.get().player() == P1 ? a : b;
            if (decision.get().kind() == DecisionKind.INTERCEPT) {
                intercepts++;
            }
            int index = TestServer.anyIndex(decision.get(), players);
            assertThat(instance.sessions.act(id, decision.get().player(), decision.get().id(), index)).isEmpty();
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
        GameId id = a.sessions.create(GameSessionServiceTest.botGame(21)).game();
        Decision decision = a.decision(id).orElseThrow();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Optional<Rejection>>> answers = List.of(a, b).stream()
                    .map(instance -> CompletableFuture.supplyAsync(() -> {
                        await(start);
                        return instance.sessions.act(id, P1, decision.id(), 0);
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
        GameId id = a.sessions.create(GameSessionServiceTest.botGame(4)).game();
        RecordingConnection human = new RecordingConnection();
        b.connections.open(id, P1, human);

        SplitMix64 clicks = new SplitMix64(1);
        int turn = 0;
        for (Optional<Decision> decision = a.decision(id); decision.isPresent(); decision = a.decision(id)) {
            TestServer instance = turn++ % 2 == 0 ? a : b;
            assertThat(instance.sessions.act(id, P1, decision.get().id(),
                    TestServer.anyIndex(decision.get(), clicks))).isEmpty();
        }

        int last = a.session(id).version();
        assertThat(a.session(id).status()).isEqualTo(GameStatus.FINISHED);
        assertThat(human.versions().getLast()).isEqualTo(last);
        assertThat(human.versions()).isSorted().doesNotHaveDuplicates();
    }

    @Test
    void aNewConnectionForASeatClosesTheOlderOneOnTheOtherInstance() {
        GameId id = a.sessions.create(GameSessionServiceTest.botGame(2)).game();
        RecordingConnection first = new RecordingConnection();
        RecordingConnection second = new RecordingConnection();

        a.connections.open(id, P1, first);
        b.connections.open(id, P1, second);

        assertThat(first.wasReplaced()).isTrue();
        assertThat(second.wasReplaced()).isFalse();
        int before = first.received().size();
        Decision decision = a.decision(id).orElseThrow();
        b.sessions.act(id, P1, decision.id(), 0);
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
