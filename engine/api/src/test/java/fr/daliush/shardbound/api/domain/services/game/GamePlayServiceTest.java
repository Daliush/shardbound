package fr.daliush.shardbound.api.domain.services.game;

import static fr.daliush.shardbound.api.testing.TestInstance.CONTENT;
import static fr.daliush.shardbound.api.testing.TestInstance.ENGINE;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.api.dao.game.GameDaoInMemory;
import fr.daliush.shardbound.api.domain.bo.command.Rejection.Reason;
import fr.daliush.shardbound.api.domain.bo.command.Rejection;
import fr.daliush.shardbound.api.domain.bo.command.SeatAccess;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.game.GameSession;
import fr.daliush.shardbound.api.domain.bo.game.GameStatus;
import fr.daliush.shardbound.api.testing.RecordingNotificationDao;
import fr.daliush.shardbound.api.testing.TestInstance;
import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.random.SplitMix64;
import fr.daliush.shardbound.core.rules.GameSetup;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GamePlayServiceTest {

    private final RecordingNotificationDao notifications = new RecordingNotificationDao();
    private final TestInstance server = new TestInstance(new GameDaoInMemory(), notifications, "a");

    @ParameterizedTest
    @ValueSource(strings = {"random", "greedy"})
    void aGameAgainstABotIsPlayedToTheEndWithOneSaveAndOneNotificationPerAction(String bot) {
        GameId id = server.creation.create(TestInstance.botGame(7, bot)).game();
        SplitMix64 human = new SplitMix64(3);

        for (Optional<Decision> decision = server.decision(id); decision.isPresent(); decision = server.decision(id)) {
            assertThat(decision.get().player()).as("bots never leave their decision pending").isEqualTo(P1);
            assertThat(server.play.act(id, P1, decision.get().id(), TestInstance.anyIndex(decision.get(), human)))
                    .isEmpty();
        }

        GameSession end = server.session(id);
        assertThat(end.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(end.actions()).hasSize(end.version());
        assertThat(notifications.versions(id.value()))
                .isEqualTo(IntStream.rangeClosed(1, end.version()).boxed().toList());
        assertThat(replay(end)).as("the setup and the action log rebuild the state").isEqualTo(end.state().get());
    }

    @Test
    void aStaleForeignOrUnknownAnswerIsRejected() {
        SeatAccess creator = server.creation.create(TestInstance.humanGame(5));
        GameId id = creator.game();
        server.creation.join(id, creator.joinCode().orElseThrow(), "root-starter");
        Decision mulligan = server.decision(id).orElseThrow();
        PlayerId decider = mulligan.player();

        assertThat(reason(server.play.act(id, decider.opponent(), mulligan.id(), 0)))
                .hasValue(Reason.NOT_YOUR_DECISION);
        assertThat(reason(server.play.act(id, decider, "d-999", 0))).hasValue(Reason.STALE_DECISION);
        assertThat(reason(server.play.act(id, decider, mulligan.id(), 2))).hasValue(Reason.INVALID_ACTION);
        assertThat(server.play.act(id, decider, mulligan.id(), 0)).isEmpty();
        assertThat(reason(server.play.act(id, decider, mulligan.id(), 0))).hasValue(Reason.STALE_DECISION);
    }

    @Test
    void anAnswerBeforeTheJoinIsRejected() {
        GameId id = server.creation.create(TestInstance.humanGame(5)).game();

        assertThat(reason(server.play.act(id, P1, "d-1", 0))).hasValue(Reason.GAME_NOT_STARTED);
    }

    @Test
    void inAGameBetweenHumansTheDefenderInterceptsDuringTheAttackersTurn() {
        GameId id = reachIntercept();
        Decision intercept = server.decision(id).orElseThrow();
        GameState state = server.session(id).state().orElseThrow();
        assertThat(intercept.player()).isNotEqualTo(state.active());

        assertThat(server.play.act(id, intercept.player(), intercept.id(), 1)).isEmpty();

        assertThat(server.session(id).events()).anyMatch(GameEvent.AttackIntercepted.class::isInstance);
    }

    /** Plays games between two humans at random until the defender is asked to intercept. */
    private GameId reachIntercept() {
        for (long seed = 1; seed < 50; seed++) {
            SeatAccess creator = server.creation.create(TestInstance.humanGame(seed));
            GameId id = creator.game();
            server.creation.join(id, creator.joinCode().orElseThrow(), "root-starter");
            SplitMix64 players = new SplitMix64(seed);
            for (Optional<Decision> decision = server.decision(id); decision.isPresent();
                 decision = server.decision(id)) {
                if (decision.get().kind() == DecisionKind.INTERCEPT) {
                    return id;
                }
                server.play.act(id, decision.get().player(), decision.get().id(),
                        TestInstance.anyIndex(decision.get(), players));
            }
        }
        throw new IllegalStateException("No intercept in 50 random games");
    }

    private static Optional<Reason> reason(Optional<Rejection> rejection) {
        return rejection.map(Rejection::reason);
    }

    private static GameState replay(GameSession session) {
        GameState state = ENGINE.newGame(new GameSetup(CONTENT.deck("ember-starter"), CONTENT.deck("root-starter"),
                session.seed())).state();
        for (Action action : session.actions()) {
            state = ENGINE.apply(state, action).state();
        }
        return state;
    }
}
