package fr.daliush.shardbound.api.controller.ws;

import fr.daliush.shardbound.api.controller.mappers.ws.ProtocolJson;
import fr.daliush.shardbound.api.controller.ws.message.ServerMessage;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.core.state.PlayerId;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * One seat's WebSocket on this instance. Sends go through Spring's decorator, since {@code sendMessage} is not
 * thread-safe; the client's messages are processed in order on the socket's own virtual thread, so WebSocket
 * threads never block.
 */
public final class SeatSocket {

    /** Spec §13.1: a newer connection took the seat. */
    static final CloseStatus REPLACED = new CloseStatus(4409, "replaced by a newer connection");

    private static final Logger LOG = LoggerFactory.getLogger(SeatSocket.class);
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT = 4 * 1024 * 1024;

    private final String id = UUID.randomUUID().toString();
    private final GameId game;
    private final PlayerId seat;
    private final WebSocketSession session;
    private final ProtocolJson json;
    private final ExecutorService inbox = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("game-socket-", 0).factory());

    public SeatSocket(GameId game, PlayerId seat, WebSocketSession session, ProtocolJson json) {
        this.game = game;
        this.seat = seat;
        this.session = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
        this.json = json;
    }

    /** Unique across instances. */
    public String id() {
        return id;
    }

    public GameId game() {
        return game;
    }

    public PlayerId seat() {
        return seat;
    }

    public void send(ServerMessage message) {
        try {
            session.sendMessage(new TextMessage(json.write(message)));
        } catch (IOException | IllegalStateException e) {
            LOG.debug("Could not send to socket {}: {}", id, e.getMessage());
        }
    }

    public void close(CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            LOG.debug("Could not close socket {}: {}", id, e.getMessage());
        }
    }

    /** Runs after the socket's earlier work; a failure is logged and closes the socket. */
    public void process(Runnable work) {
        inbox.execute(() -> {
            try {
                work.run();
            } catch (RuntimeException e) {
                LOG.error("Game {}, seat {}: message failed", game, seat, e);
                close(CloseStatus.SERVER_ERROR);
            }
        });
    }

    /** Lets the work already queued finish, then stops the socket's thread. */
    public void shutdown() {
        inbox.shutdown();
    }
}
