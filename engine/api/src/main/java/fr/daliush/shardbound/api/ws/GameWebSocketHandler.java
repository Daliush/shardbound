package fr.daliush.shardbound.api.ws;

import fr.daliush.shardbound.api.protocol.ClientMessage;
import fr.daliush.shardbound.api.protocol.ProtocolJson;
import fr.daliush.shardbound.api.protocol.ServerMessage;
import fr.daliush.shardbound.api.session.GameId;
import fr.daliush.shardbound.api.session.GameSessionService;
import fr.daliush.shardbound.api.session.PlayerConnections;
import fr.daliush.shardbound.api.session.Rejection;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * One handler for every game's WebSocket. WebSocket threads never block: each connection hands its messages,
 * in order, to its own virtual thread, which calls the session server (spec §13.4).
 */
final class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private static final String SEATED = "shardbound.seated";

    private final GameSessionService sessions;
    private final PlayerConnections connections;
    private final ProtocolJson json = new ProtocolJson();

    GameWebSocketHandler(GameSessionService sessions, PlayerConnections connections) {
        this.sessions = sessions;
        this.connections = connections;
    }

    /** A connection's seat, and the virtual thread that processes its messages one after another. */
    private record Seated(GameId game, PlayerId seat, WebSocketSeatConnection connection, ExecutorService inbox) {}

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Seated seated = new Seated((GameId) session.getAttributes().get(GameHandshakeInterceptor.GAME),
                (PlayerId) session.getAttributes().get(GameHandshakeInterceptor.SEAT),
                new WebSocketSeatConnection(session),
                Executors.newSingleThreadExecutor(Thread.ofVirtual().name("game-socket-", 0).factory()));
        session.getAttributes().put(SEATED, seated);
        process(seated, () -> connections.open(seated.game(), seated.seat(), seated.connection()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Seated seated = seated(session);
        process(seated, () -> answer(seated, message.getPayload()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Seated seated = seated(session);
        process(seated, () -> connections.closed(seated.game(), seated.connection()));
        seated.inbox().shutdown();
    }

    private void answer(Seated seated, String text) {
        switch (json.read(text).orElse(null)) {
            case ClientMessage.Act act when act.isComplete() -> sessions
                    .act(seated.game(), seated.seat(), act.decisionId(), act.action())
                    .ifPresent(rejection -> reject(seated, act.requestId(), rejection));
            case ClientMessage.Act act -> reject(seated, act.requestId(), malformed("An act needs a decisionId and"
                    + " an action index."));
            case ClientMessage.Sync ignored -> connections.sync(seated.game(), seated.connection());
            case null -> reject(seated, null, malformed("Not a message of the game protocol."));
        }
    }

    private void reject(Seated seated, String requestId, Rejection rejection) {
        seated.connection().send(json.write(new ServerMessage.Rejected(requestId, rejection.reason().code(),
                rejection.message())));
    }

    private static Rejection malformed(String message) {
        return new Rejection(Rejection.Reason.MALFORMED_MESSAGE, message);
    }

    private static void process(Seated seated, Runnable work) {
        seated.inbox().execute(() -> {
            try {
                work.run();
            } catch (RuntimeException e) {
                LOG.error("Game {}, seat {}: message failed", seated.game(), seated.seat(), e);
                seated.connection().close(CloseStatus.SERVER_ERROR);
            }
        });
    }

    private static Seated seated(WebSocketSession session) {
        return (Seated) session.getAttributes().get(SEATED);
    }
}
