package fr.daliush.shardbound.api.ws;

import fr.daliush.shardbound.api.session.SeatConnection;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/** A seat's WebSocket. {@code sendMessage} is not thread-safe, so every send goes through Spring's decorator. */
final class WebSocketSeatConnection implements SeatConnection {

    /** Spec §13.1: a newer connection took the seat. */
    static final CloseStatus REPLACED = new CloseStatus(4409, "replaced by a newer connection");

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketSeatConnection.class);
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT = 4 * 1024 * 1024;

    private final String id = UUID.randomUUID().toString();
    private final WebSocketSession session;

    WebSocketSeatConnection(WebSocketSession session) {
        this.session = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void send(String message) {
        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException | IllegalStateException e) {
            LOG.debug("Could not send to connection {}: {}", id, e.getMessage());
        }
    }

    @Override
    public void replaced() {
        close(REPLACED);
    }

    void close(CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            LOG.debug("Could not close connection {}: {}", id, e.getMessage());
        }
    }
}
