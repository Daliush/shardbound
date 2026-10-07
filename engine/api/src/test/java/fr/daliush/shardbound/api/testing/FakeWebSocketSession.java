package fr.daliush.shardbound.api.testing;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A WebSocket that keeps every message it is sent, and how it was closed. */
public final class FakeWebSocketSession implements WebSocketSession {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String id = UUID.randomUUID().toString();
    private final Map<String, Object> attributes = new HashMap<>();
    private final List<String> sent = new CopyOnWriteArrayList<>();
    private volatile CloseStatus closeStatus;

    public List<JsonNode> received() {
        return sent.stream().map(JSON::readTree).toList();
    }

    /** The {@code view.version} of each message, in the order they arrived. */
    public List<Integer> versions() {
        return received().stream().map(message -> message.get("view").get("version").asInt()).toList();
    }

    public List<String> types() {
        return received().stream().map(message -> message.get("type").asString()).toList();
    }

    public CloseStatus closeStatus() {
        return closeStatus;
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) {
        sent.add(((TextMessage) message).getPayload());
    }

    @Override
    public boolean isOpen() {
        return closeStatus == null;
    }

    @Override
    public void close() {
        close(CloseStatus.NORMAL);
    }

    @Override
    public void close(CloseStatus status) {
        closeStatus = status;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public URI getUri() {
        return null;
    }

    @Override
    public HttpHeaders getHandshakeHeaders() {
        return new HttpHeaders();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Principal getPrincipal() {
        return null;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return null;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return null;
    }

    @Override
    public String getAcceptedProtocol() {
        return null;
    }

    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {
    }

    @Override
    public int getTextMessageSizeLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {
    }

    @Override
    public int getBinaryMessageSizeLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public List<WebSocketExtension> getExtensions() {
        return List.of();
    }
}
