package fr.daliush.shardbound.api.domain.services.game;

import fr.daliush.shardbound.api.domain.bo.game.GameSession;
import fr.daliush.shardbound.api.domain.bo.game.Seat;
import fr.daliush.shardbound.api.domain.bo.game.SeatUpdate;
import fr.daliush.shardbound.api.domain.mappers.view.SeatSnapshotMapper;
import fr.daliush.shardbound.api.domain.ports.GameNotificationPort;
import fr.daliush.shardbound.api.domain.ports.GameSessionPort;
import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.Transition;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.text.ActionDescriber;
import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The step every change of a game ends with: each human seat's update goes to the outbox, the session is saved
 * with an optimistic lock, and every instance hears of the new version (spec §13.4).
 */
@Component
public class GameSaver {

    private static final Logger LOG = LoggerFactory.getLogger(GameSaver.class);

    private final GameEngine engine;
    private final GameSessionPort sessions;
    private final GameNotificationPort notifications;
    private final SeatSnapshotMapper snapshots;
    private final Clock clock;
    private final GameSettings settings;
    private final ActionDescriber labels;

    public GameSaver(GameEngine engine, GameSessionPort sessions, GameNotificationPort notifications,
                     SeatSnapshotMapper snapshots, Clock clock, GameSettings settings) {
        this.engine = engine;
        this.sessions = sessions;
        this.notifications = notifications;
        this.snapshots = snapshots;
        this.clock = clock;
        this.settings = settings;
        this.labels = new ActionDescriber(engine.catalog());
    }

    /** Applies one of the pending decision's actions and saves the result; false when another save came first. */
    public boolean apply(GameSession session, Action action) {
        GameState state = session.state().orElseThrow();
        Decision decision = engine.decision(state).orElseThrow();
        Transition transition = engine.apply(state, action);
        if (!save(session.applied(action, transition, clock.instant()), session.version(), transition.events())) {
            return false;
        }
        LOG.info("game={} seat={} decision={} action=\"{}\" version={} instance={}", session.id(),
                decision.player(), decision.kind(), labels.describe(action, state, decision.player()),
                session.version() + 1, settings.instance());
        return true;
    }

    /** Saves a session whose last change produced {@code newEvents}; false when another save came first. */
    public boolean save(GameSession next, int expectedVersion, List<GameEvent> newEvents) {
        GameSession withUpdates = next.withOutbox(next.outbox().with(next.version(), updates(next, newEvents)));
        if (!sessions.save(withUpdates, expectedVersion)) {
            return false;
        }
        notifications.publish(next.id(), next.version());
        return true;
    }

    private List<SeatUpdate> updates(GameSession session, List<GameEvent> newEvents) {
        GameState state = session.state().orElseThrow();
        return Stream.of(PlayerId.values())
                .filter(seat -> session.seat(seat) instanceof Seat.Human)
                .map(seat -> new SeatUpdate(session.version(), seat, snapshots.toSnapshot(state, seat),
                        engine.eventsFor(newEvents, seat)))
                .toList();
    }
}
