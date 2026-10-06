package fr.daliush.shardbound.api.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fr.daliush.shardbound.api.dto.GameView;
import java.util.List;
import java.util.Map;

/** What the server sends to one seat. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ServerMessage.State.class, name = "state"),
    @JsonSubTypes.Type(value = ServerMessage.Update.class, name = "update"),
    @JsonSubTypes.Type(value = ServerMessage.Rejected.class, name = "rejected")
})
public sealed interface ServerMessage {

    /** The full view and the whole history, redacted for the seat: on connection and in answer to a sync. */
    record State(GameView view, List<Map<String, Object>> history) implements ServerMessage {}

    /** The view after one saved change, and the events of that change. */
    record Update(GameView view, List<Map<String, Object>> events) implements ServerMessage {}

    /** An answer to the sender only: its message was not applied. */
    record Rejected(String requestId, String reason, String message) implements ServerMessage {}
}
