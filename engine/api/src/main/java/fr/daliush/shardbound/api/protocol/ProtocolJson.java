package fr.daliush.shardbound.api.protocol;

import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** The protocol's JSON. Nullable fields are written as explicit {@code null} (spec §13.3). */
public final class ProtocolJson {

    private final JsonMapper mapper = JsonMapper.builder().build();

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
