package fr.daliush.shardbound.api.session;

import fr.daliush.shardbound.api.dto.EventViews;
import fr.daliush.shardbound.api.dto.GameView;
import fr.daliush.shardbound.api.dto.GameViews;
import fr.daliush.shardbound.api.protocol.ProtocolJson;
import fr.daliush.shardbound.api.protocol.ServerMessage;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.DeckId;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The protocol messages about a session, each written for one seat from what the engine lets it see. */
final class SessionMessages {

    private final Content content;
    private final GameViews views;
    private final EventViews events;
    private final ProtocolJson json = new ProtocolJson();

    SessionMessages(Content content, GameEngine engine) {
        this.content = content;
        this.views = new GameViews(engine);
        this.events = new EventViews(engine);
    }

    /** The full view and the whole history: on connection and on sync. */
    String state(GameSession session, PlayerId seat) {
        if (session.status() == GameStatus.WAITING_FOR_OPPONENT) {
            DeckId deck = ((Seat.Human) session.seat(seat)).deck();
            return json.write(new ServerMessage.State(views.waiting(session.id().toString(), content.deck(deck.value())),
                    List.of()));
        }
        return json.write(new ServerMessage.State(view(session, seat), events.of(session.events(), seat)));
    }

    /** One update per human seat for the session's latest save, which produced {@code newEvents}. */
    Map<PlayerId, String> updates(GameSession session, List<GameEvent> newEvents) {
        Map<PlayerId, String> updates = new EnumMap<>(PlayerId.class);
        for (PlayerId seat : PlayerId.values()) {
            if (session.seat(seat) instanceof Seat.Human) {
                updates.put(seat, json.write(new ServerMessage.Update(view(session, seat),
                        events.of(newEvents, seat))));
            }
        }
        return updates;
    }

    private GameView view(GameSession session, PlayerId seat) {
        return views.of(session.id().toString(), session.version(), session.state().orElseThrow(), seat);
    }
}
