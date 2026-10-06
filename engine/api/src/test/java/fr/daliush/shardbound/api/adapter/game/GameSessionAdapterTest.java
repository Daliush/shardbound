package fr.daliush.shardbound.api.adapter.game;

import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.api.dao.game.GameDaoInMemory;
import fr.daliush.shardbound.api.dao.notification.GameNotificationDaoInMemory;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.game.GameSession;
import fr.daliush.shardbound.api.testing.TestInstance;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.random.SplitMix64;
import org.junit.jupiter.api.Test;

/**
 * A session goes through the entity mapper and the in-memory DAO, which keeps it as JSON like a table row
 * would: nothing may be lost on the way (spec §16).
 */
class GameSessionAdapterTest {

    private final GameDaoInMemory dao = new GameDaoInMemory();
    private final TestInstance server = new TestInstance(dao, new GameNotificationDaoInMemory(), "a");

    @Test
    void aPlayedSessionComesBackUnchanged() {
        GameId id = server.creation.create(TestInstance.botGame(13)).game();
        SplitMix64 clicks = new SplitMix64(2);
        for (int i = 0; i < 15; i++) {
            Decision decision = server.decision(id).orElseThrow();
            server.play.act(id, P1, decision.id(), TestInstance.anyIndex(decision, clicks));
        }
        GameSession session = server.session(id);
        GameId copy = GameId.random();
        GameSession original = withId(session, copy);

        server.sessions.create(original);

        assertThat(session.outbox().updates()).isNotEmpty();
        assertThat(server.sessions.find(copy)).hasValue(original);
    }

    @Test
    void aWaitingSessionComesBackUnchanged() {
        GameId id = server.creation.create(TestInstance.humanGame(5)).game();
        GameSession session = server.session(id);
        GameSession original = withId(session, GameId.random());

        server.sessions.create(original);

        assertThat(server.sessions.find(original.id())).hasValue(original);
    }

    @Test
    void aSaveAgainstAnOldVersionIsRefused() {
        GameId id = server.creation.create(TestInstance.botGame(13)).game();
        GameSession session = server.session(id);

        assertThat(server.sessions.save(session, session.version() + 1)).isFalse();
        assertThat(server.sessions.save(session, session.version())).isTrue();
    }

    private static GameSession withId(GameSession session, GameId id) {
        return new GameSession(id, session.status(), session.seed(), session.p1(), session.p2(), session.state(),
                session.version(), session.events(), session.actions(), session.outbox(), session.lastActivity());
    }
}
