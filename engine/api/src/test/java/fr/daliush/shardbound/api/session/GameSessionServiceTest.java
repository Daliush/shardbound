package fr.daliush.shardbound.api.session;

import static fr.daliush.shardbound.api.session.TestServer.CONTENT;
import static fr.daliush.shardbound.api.session.TestServer.ENGINE;
import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.daliush.shardbound.api.session.Rejection.Reason;
import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.random.SplitMix64;
import fr.daliush.shardbound.core.rules.GameSetup;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GameSessionServiceTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RecordingUpdates updates = new RecordingUpdates();
    private final TestServer server = new TestServer(new GameRepositoryInMemory(), updates, "a");
    private final GameSessionService sessions = server.sessions;

    @Test
    void aGameAgainstABotIsPlayedToTheEndWithOneSaveAndOneNotificationPerAction() {
        GameId id = sessions.create(botGame(7)).game();
        SplitMix64 human = new SplitMix64(3);

        for (Optional<Decision> decision = server.decision(id); decision.isPresent(); decision = server.decision(id)) {
            assertThat(decision.get().player()).as("bots never leave their decision pending").isEqualTo(P1);
            assertThat(sessions.act(id, P1, decision.get().id(), TestServer.anyIndex(decision.get(), human)))
                    .isEmpty();
        }

        GameSession end = server.session(id);
        assertThat(end.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(end.actions()).hasSize(end.version());
        assertThat(updates.versions(id)).isEqualTo(IntStream.rangeClosed(1, end.version()).boxed().toList());
        assertThat(replay(end)).as("the setup and the action log rebuild the state").isEqualTo(end.state().get());
    }

    @Test
    void aStaleForeignOrUnknownAnswerIsRejected() {
        SeatAccess creator = sessions.create(humanGame());
        GameId id = creator.game();
        sessions.join(id, creator.joinCode().orElseThrow(), "root-starter");
        Decision mulligan = server.decision(id).orElseThrow();
        PlayerId decider = mulligan.player();

        assertThat(reason(sessions.act(id, decider.opponent(), mulligan.id(), 0))).hasValue(Reason.NOT_YOUR_DECISION);
        assertThat(reason(sessions.act(id, decider, "d-999", 0))).hasValue(Reason.STALE_DECISION);
        assertThat(reason(sessions.act(id, decider, mulligan.id(), 2))).hasValue(Reason.INVALID_ACTION);
        assertThat(sessions.act(id, decider, mulligan.id(), 0)).isEmpty();
        assertThat(reason(sessions.act(id, decider, mulligan.id(), 0))).hasValue(Reason.STALE_DECISION);
    }

    @Test
    void aGameAgainstAHumanWaitsForTheJoinWhichIsSavedAsVersionOne() {
        SeatAccess creator = sessions.create(humanGame());
        GameId id = creator.game();

        JsonNode waiting = JSON.readTree(sessions.state(id, P1).message());
        assertThat(waiting.at("/view/status").asString()).isEqualTo("waiting_for_opponent");
        assertThat(waiting.at("/view/opponent").isNull()).isTrue();
        assertThat(reason(sessions.act(id, P1, "d-1", 0))).hasValue(Reason.GAME_NOT_STARTED);
        assertThatThrownBy(() -> sessions.join(id, "wrong", "root-starter"))
                .isInstanceOf(SessionException.JoinRefused.class);

        SeatAccess joiner = sessions.join(id, creator.joinCode().orElseThrow(), "root-starter");

        GameSession started = server.session(id);
        assertThat(started.version()).isEqualTo(1);
        assertThat(updates.versions(id)).containsExactly(1);
        JsonNode update = JSON.readTree(started.outbox().after(0, 1, P1).orElseThrow().getFirst());
        assertThat(update.get("type").asString()).isEqualTo("update");
        assertThat(update.at("/events/0/type").asString()).isEqualTo("game_started");
        assertThat(sessions.authenticate(id, creator.playerToken())).isEqualTo(P1);
        assertThat(sessions.authenticate(id, joiner.playerToken())).isEqualTo(P2);
        assertThatThrownBy(() -> sessions.join(id, creator.joinCode().orElseThrow(), "root-starter"))
                .isInstanceOf(SessionException.JoinRefused.class);
        assertThatThrownBy(() -> sessions.authenticate(id, "not-a-token"))
                .isInstanceOf(SessionException.InvalidToken.class);
    }

    @Test
    void inAGameBetweenHumansTheDefenderInterceptsDuringTheAttackersTurn() {
        GameId id = Interception.reachIntercept(server, sessions);
        Decision intercept = server.decision(id).orElseThrow();
        GameState state = server.session(id).state().orElseThrow();
        assertThat(intercept.player()).isNotEqualTo(state.active());

        assertThat(sessions.act(id, intercept.player(), intercept.id(), 1)).isEmpty();

        assertThat(server.session(id).events()).anyMatch(GameEvent.AttackIntercepted.class::isInstance);
    }

    @Test
    void syncSendsTheFullViewAndTheWholeHistoryRedactedForTheSeat() {
        GameId id = sessions.create(botGame(11)).game();
        GameSession session = server.session(id);

        JsonNode state = JSON.readTree(sessions.state(id, P1).message());

        assertThat(state.get("type").asString()).isEqualTo("state");
        assertThat(state.at("/view/version").asInt()).isEqualTo(session.version());
        assertThat(state.get("history")).hasSize(session.events().size());
        assertThat(state.at("/view/opponent/handCount").asInt()).isPositive();
        assertThat(state.at("/view/opponent").has("hand")).isFalse();
        assertThat(state.get("history").valueStream()
                .filter(event -> event.get("type").asString().equals("card_drawn"))
                .filter(event -> event.get("player").asString().equals("opponent")))
                .allMatch(event -> event.get("card").isNull());
    }

    @Test
    void refusesUnknownDecksBotsAndGames() {
        assertThatThrownBy(() -> sessions.create(new NewGame("emberr-starter", new NewGame.Opponent.Human(),
                OptionalLong.empty()))).isInstanceOf(SessionException.InvalidRequest.class)
                .hasMessage("Unknown deck: emberr-starter");
        assertThatThrownBy(() -> sessions.create(new NewGame("ember-starter",
                new NewGame.Opponent.Bot("genius", "root-starter"), OptionalLong.empty())))
                .isInstanceOf(SessionException.InvalidRequest.class);
        assertThatThrownBy(() -> sessions.join(GameId.random(), "code", "root-starter"))
                .isInstanceOf(SessionException.UnknownGame.class);
    }

    static NewGame botGame(long seed) {
        return new NewGame("ember-starter", new NewGame.Opponent.Bot("random", "root-starter"), OptionalLong.of(seed));
    }

    static NewGame humanGame() {
        return new NewGame("ember-starter", new NewGame.Opponent.Human(), OptionalLong.of(5));
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

    /** Plays a game between two humans at random until the defender is asked to intercept. */
    static final class Interception {

        static GameId reachIntercept(TestServer server, GameSessionService sessions) {
            for (long seed = 1; seed < 50; seed++) {
                SeatAccess creator = sessions.create(new NewGame("ember-starter", new NewGame.Opponent.Human(),
                        OptionalLong.of(seed)));
                GameId id = creator.game();
                sessions.join(id, creator.joinCode().orElseThrow(), "root-starter");
                SplitMix64 players = new SplitMix64(seed);
                for (Optional<Decision> decision = server.decision(id); decision.isPresent();
                     decision = server.decision(id)) {
                    if (decision.get().kind() == DecisionKind.INTERCEPT) {
                        return id;
                    }
                    sessions.act(id, decision.get().player(), decision.get().id(),
                            TestServer.anyIndex(decision.get(), players));
                }
            }
            throw new IllegalStateException("No intercept in 50 random games");
        }
    }
}
