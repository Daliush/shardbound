package fr.daliush.shardbound.api.controller.mappers.ws;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import fr.daliush.shardbound.api.controller.ws.message.ClientMessage;
import fr.daliush.shardbound.api.controller.ws.message.ServerMessage;
import fr.daliush.shardbound.api.domain.bo.view.ActionView;
import fr.daliush.shardbound.api.domain.bo.view.EventTargetView;
import fr.daliush.shardbound.api.domain.bo.view.EventView;
import fr.daliush.shardbound.api.domain.bo.view.TargetView;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The protocol's JSON (spec §13.3). Nullable fields are written as explicit {@code null}; the fields that exist
 * only for some kinds of actions and targets are left out; an event's fields sit beside its type. The business
 * objects carry no JSON annotation: the mix-ins below say all of this here.
 */
@Component
public class ProtocolJson {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private interface OnlyPresentFields {
    }

    private interface FlatEvent {
        @JsonAnyGetter
        Map<String, Object> fields();
    }

    private final JsonMapper mapper = JsonMapper.builder()
            .addMixIn(ActionView.class, OnlyPresentFields.class)
            .addMixIn(TargetView.class, OnlyPresentFields.class)
            .addMixIn(EventTargetView.class, OnlyPresentFields.class)
            .addMixIn(EventView.class, FlatEvent.class)
            .build();

    public String write(ServerMessage message) {
        return mapper.writerFor(ServerMessage.class).writeValueAsString(message);
    }

    /** Empty when the text is not a client message. */
    public Optional<ClientMessage> read(String json) {
        try {
            return Optional.ofNullable(mapper.readValue(json, ClientMessage.class));
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }
}
