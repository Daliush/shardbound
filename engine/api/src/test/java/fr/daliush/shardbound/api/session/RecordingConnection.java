package fr.daliush.shardbound.api.session;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A connection that keeps every message it was sent. */
final class RecordingConnection implements SeatConnection {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String id = UUID.randomUUID().toString();
    private final List<String> messages = new CopyOnWriteArrayList<>();
    private volatile boolean replaced;

    @Override
    public String id() {
        return id;
    }

    @Override
    public void send(String message) {
        messages.add(message);
    }

    @Override
    public void replaced() {
        replaced = true;
    }

    boolean wasReplaced() {
        return replaced;
    }

    List<JsonNode> received() {
        return messages.stream().map(JSON::readTree).toList();
    }

    /** The {@code view.version} of each message, in the order they arrived. */
    List<Integer> versions() {
        return received().stream().map(message -> message.get("view").get("version").asInt()).toList();
    }

    List<String> types() {
        return received().stream().map(message -> message.get("type").asString()).toList();
    }
}
