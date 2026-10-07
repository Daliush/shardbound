package fr.daliush.shardbound.api.controller.ws.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fr.daliush.shardbound.api.domain.bo.view.EventView;
import fr.daliush.shardbound.api.domain.bo.view.GameView;
import java.util.List;

/** What the server sends to one seat (spec §13.2). */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ServerMessage.State.class, name = "state"),
    @JsonSubTypes.Type(value = ServerMessage.Update.class, name = "update"),
    @JsonSubTypes.Type(value = ServerMessage.Rejected.class, name = "rejected")
})
public sealed interface ServerMessage {

    /** The full view and the whole history, redacted for the seat: on connection and in answer to a sync. */
    record State(GameView view, List<EventView> history) implements ServerMessage {}

    /** The view after one saved change, and the events of that change. */
    record Update(GameView view, List<EventView> events) implements ServerMessage {}

    /** An answer to the sender only: its message was not applied. */
    record Rejected(String requestId, String reason, String message) implements ServerMessage {}
}
