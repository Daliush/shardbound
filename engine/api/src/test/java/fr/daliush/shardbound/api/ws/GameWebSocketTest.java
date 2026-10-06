package fr.daliush.shardbound.api.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.daliush.shardbound.api.session.GameSessionService;
import fr.daliush.shardbound.api.session.NewGame;
import fr.daliush.shardbound.api.session.SeatAccess;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A real WebSocket client against the server on a random port (spec §16). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameWebSocketTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private GameSessionService sessions;

    @Test
    void sendsTheStateOnConnectionThenAnUpdateForEachAppliedAction() throws Exception {
        SeatAccess access = botGame();
        Client client = connect(access.game().toString(), access.playerToken());

        JsonNode state = client.next();
        assertThat(state.get("type").asString()).isEqualTo("state");
        assertThat(state.get("history").isEmpty()).isFalse();
        JsonNode decision = state.at("/view/decision");
        assertThat(decision.isNull()).as("bots play at once, so the decision is the human's").isFalse();
        int version = state.at("/view/version").asInt();

        client.send("{ \"type\": \"act\", \"requestId\": \"c-1\", \"decisionId\": \""
                + decision.get("id").asString() + "\", \"action\": 0 }");

        JsonNode update = client.next();
        assertThat(update.get("type").asString()).isEqualTo("update");
        assertThat(update.at("/view/version").asInt()).isEqualTo(version + 1);
        assertThat(update.get("events").isEmpty()).isFalse();
    }

    @Test
    void rejectsAStaleOrMalformedMessageToTheSenderOnly() throws Exception {
        SeatAccess access = botGame();
        Client client = connect(access.game().toString(), access.playerToken());
        client.next();

        client.send("{ \"type\": \"act\", \"requestId\": \"c-7\", \"decisionId\": \"d-999\", \"action\": 0 }");
        JsonNode stale = client.next();
        assertThat(stale.get("type").asString()).isEqualTo("rejected");
        assertThat(stale.get("requestId").asString()).isEqualTo("c-7");
        assertThat(stale.get("reason").asString()).isEqualTo("stale_decision");

        client.send("hello");
        assertThat(client.next().get("reason").asString()).isEqualTo("malformed_message");

        client.send("{ \"type\": \"sync\" }");
        assertThat(client.next().get("type").asString()).isEqualTo("state");
    }

    @Test
    void refusesAnUnknownGameOrABadToken() {
        SeatAccess access = botGame();

        assertThatThrownBy(() -> connect(access.game().toString(), "not-a-token"))
                .hasStackTraceContaining("401");
        assertThatThrownBy(() -> connect("8f2c6d1e-3b7a-4c59-9e10-5a4b2d7f9c31", access.playerToken()))
                .hasStackTraceContaining("404");
    }

    @Test
    void aSecondConnectionForTheSameSeatReplacesTheFirst() throws Exception {
        SeatAccess access = botGame();
        Client first = connect(access.game().toString(), access.playerToken());
        first.next();

        Client second = connect(access.game().toString(), access.playerToken());

        assertThat(second.next().get("type").asString()).isEqualTo("state");
        assertThat(first.closed.get(WAIT.toSeconds(), TimeUnit.SECONDS).getCode()).isEqualTo(4409);
    }

    private SeatAccess botGame() {
        return sessions.create(new NewGame("ember-starter", new NewGame.Opponent.Bot("random", "root-starter"),
                OptionalLong.of(42)));
    }

    private Client connect(String game, String token) throws Exception {
        Client client = new Client();
        client.session = new StandardWebSocketClient()
                .execute(client, "ws://localhost:" + port + "/ws/games/" + game + "?token=" + token)
                .get(WAIT.toSeconds(), TimeUnit.SECONDS);
        return client;
    }

    /** Keeps every message the server sends, and how the connection closed. */
    private static final class Client extends TextWebSocketHandler {
        private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        private final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        private WebSocketSession session;

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            received.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed.complete(status);
        }

        JsonNode next() throws InterruptedException {
            String message = received.poll(WAIT.toSeconds(), TimeUnit.SECONDS);
            assertThat(message).as("a message from the server").isNotNull();
            return JSON.readTree(message);
        }

        void send(String text) throws Exception {
            session.sendMessage(new TextMessage(text));
        }
    }
}
