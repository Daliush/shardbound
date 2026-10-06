package fr.daliush.shardbound.api.session;

import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.json.GameJson;
import fr.daliush.shardbound.core.random.SplitMix64;
import org.junit.jupiter.api.Test;

/** A shared store holds sessions as JSON: nothing may be lost on the way (spec §16). */
class GameSessionJsonTest {

    private final TestServer server = new TestServer(new GameRepositoryInMemory(), new GameUpdatesInMemory(), "a");
    private final GameJson json = new GameJson();

    @Test
    void aSessionSurvivesAJsonRoundTripUnchanged() {
        GameId id = server.sessions.create(GameSessionServiceTest.botGame(13)).game();
        SplitMix64 clicks = new SplitMix64(2);
        for (int i = 0; i < 15; i++) {
            Decision decision = server.decision(id).orElseThrow();
            server.sessions.act(id, P1, decision.id(), TestServer.anyIndex(decision, clicks));
        }
        GameSession session = server.session(id);

        assertThat(session.outbox().updates()).isNotEmpty();
        assertThat(json.readValue(json.writeValue(session), GameSession.class)).isEqualTo(session);
    }

    @Test
    void aWaitingSessionSurvivesAJsonRoundTripUnchanged() {
        GameId id = server.sessions.create(GameSessionServiceTest.humanGame()).game();
        GameSession session = server.session(id);

        assertThat(json.readValue(json.writeValue(session), GameSession.class)).isEqualTo(session);
    }
}
