package fr.daliush.shardbound.api.domain.mappers.view;

import fr.daliush.shardbound.api.domain.bo.view.CardRef;
import fr.daliush.shardbound.api.domain.bo.view.EventTargetView;
import fr.daliush.shardbound.api.domain.bo.view.EventView;
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
import org.springframework.stereotype.Component;

/**
 * Events as a seat may see them, one to one with the engine's records (spec §13.3): redacted for the seat, the
 * record name in snake_case as {@code type}, its rules, the engine's sentence, then its components. A new
 * engine event needs no change here.
 */
@Component
public class EventViewMapper {

    private final GameEngine engine;
    private final EventDescriber describer;

    public EventViewMapper(GameEngine engine) {
        this.engine = engine;
        this.describer = new EventDescriber(engine.catalog());
    }

    /** Redacts first, so a seat never gets what it may not see, even from an unredacted log. */
    public List<EventView> toViews(List<GameEvent> events, PlayerId seat) {
        return engine.eventsFor(events, seat).stream().map(event -> toView(event, seat)).toList();
    }

    private EventView toView(GameEvent event, PlayerId seat) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (RecordComponent component : event.getClass().getRecordComponents()) {
            if (!component.getName().equals("rules")) {
                fields.put(component.getName(), wire(read(component, event), seat));
            }
        }
        return new EventView(Wire.snake(event.getClass().getSimpleName()), event.rules(),
                describer.describe(event, seat), fields);
    }

    private static Object wire(Object value, PlayerId seat) {
        return switch (value) {
            case null -> null;
            case PlayerId player -> Wire.side(seat, player);
            case CardInstance card -> CardRef.of(card);
            case EventTarget.UnitHit hit -> EventTargetView.unit(CardRef.of(hit.unit()));
            case EventTarget.PlayerHit hit -> EventTargetView.player(Wire.side(seat, hit.player()));
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
}
