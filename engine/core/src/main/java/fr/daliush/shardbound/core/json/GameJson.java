package fr.daliush.shardbound.core.json;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.event.EventTarget;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.EffectList;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.state.GameResult;
import fr.daliush.shardbound.core.state.GameState;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.NamedType;

/**
 * Game states and events to JSON and back, so a shared store can hold a game between two messages.
 * Records are read and written through their components; sealed types carry their record name.
 */
public final class GameJson {

    private static final List<Class<?>> SEALED_ROOTS = List.of(
            GameResult.class, Step.class, EffectList.class, Action.class, TargetRef.class, GameEvent.class,
            EventTarget.class);

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    private interface Typed {
    }

    private final JsonMapper mapper;

    public GameJson() {
        JsonMapper.Builder builder = JsonMapper.builder()
                .changeDefaultVisibility(visibility -> visibility
                        .withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                        .withVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY));
        for (Class<?> root : SEALED_ROOTS) {
            builder.addMixIn(root, Typed.class);
            builder.registerSubtypes(namedSubtypes(root).toArray(NamedType[]::new));
        }
        mapper = builder.build();
    }

    public String write(GameState state) {
        return mapper.writeValueAsString(state);
    }

    public GameState readState(String json) {
        return mapper.readValue(json, GameState.class);
    }

    public String writeEvents(List<GameEvent> events) {
        return mapper.writerFor(new TypeReference<List<GameEvent>>() { }).writeValueAsString(events);
    }

    public List<GameEvent> readEvents(String json) {
        return mapper.readValue(json, new TypeReference<List<GameEvent>>() { });
    }

    private static List<NamedType> namedSubtypes(Class<?> sealed) {
        List<NamedType> types = new ArrayList<>();
        for (Class<?> subtype : sealed.getPermittedSubclasses()) {
            if (subtype.isSealed()) {
                types.addAll(namedSubtypes(subtype));
            } else {
                types.add(new NamedType(subtype, subtype.getSimpleName()));
            }
        }
        return types;
    }
}
