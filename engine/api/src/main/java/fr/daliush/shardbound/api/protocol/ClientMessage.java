package fr.daliush.shardbound.api.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** What a client sends: an answer to its decision, by index, or a request for the full state. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ClientMessage.Act.class, name = "act"),
    @JsonSubTypes.Type(value = ClientMessage.Sync.class, name = "sync")
})
public sealed interface ClientMessage {

    record Act(String requestId, String decisionId, Integer action) implements ClientMessage {

        public boolean isComplete() {
            return decisionId != null && action != null;
        }
    }

    record Sync() implements ClientMessage {}
}
