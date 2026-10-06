package fr.daliush.shardbound.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.event.EventTarget;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameResult;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.text.EventDescriber;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Events as a seat may see them, one to one with the engine's records (spec §13.3): redacted for the seat, the
 * record name in snake_case as {@code type}, its rules, the engine's sentence as {@code text}, then its
 * components. A new engine event needs no change here.
 */
public final class EventViews {

    private final GameEngine engine;
    private final EventDescriber describer;

    public EventViews(GameEngine engine) {
        this.engine = engine;
        this.describer = new EventDescriber(engine.catalog());
    }

    public List<Map<String, Object>> of(List<GameEvent> events, PlayerId seat) {
        return engine.eventsFor(events, seat).stream().map(event -> view(event, seat)).toList();
    }

    private Map<String, Object> view(GameEvent event, PlayerId seat) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("type", Wire.snake(event.getClass().getSimpleName()));
        view.put("rules", event.rules());
        view.put("text", describer.describe(event, seat));
        for (RecordComponent component : event.getClass().getRecordComponents()) {
            if (!component.getName().equals("rules")) {
                view.put(component.getName(), wire(read(component, event), seat));
            }
        }
        return view;
    }

    private static Object wire(Object value, PlayerId seat) {
        return switch (value) {
            case null -> null;
            case PlayerId player -> Wire.side(seat, player);
            case CardInstance card -> CardRef.of(card);
            case EventTarget.UnitHit hit -> new TargetSeen("unit", hit.unit().id().value(), hit.unit().card().value(),
                    null);
            case EventTarget.PlayerHit hit -> new TargetSeen("player", null, null, Wire.side(seat, hit.player()));
            case GameResult result -> Wire.result(result, seat);
            case Optional<?> optional -> wire(optional.orElse(null), seat);
            case OptionalInt optional -> optional.isPresent() ? optional.getAsInt() : null;
            case CardId card -> card.value();
            case InstanceId id -> id.value();
            case Enum<?> constant -> Wire.name(constant);
            case Integer number -> number;
            case Boolean flag -> flag;
            case String text -> text;
            case List<?> list -> list.stream().map(item -> wire(item, seat)).toList();
            default -> throw new IllegalArgumentException("No wire form for " + value.getClass().getName());
        };
    }

    private static Object read(RecordComponent component, GameEvent event) {
        try {
            return component.getAccessor().invoke(event);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Cannot read " + component.getName() + " of " + event, e);
        }
    }

    /** A unit by instance and card id, or a player by side. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TargetSeen(String kind, Integer id, String card, String player) {
    }
}
