package fr.daliush.shardbound.api.domain.services.update;

import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.game.GameSession;
import fr.daliush.shardbound.api.domain.bo.game.GameStatus;
import fr.daliush.shardbound.api.domain.bo.game.Seat;
import fr.daliush.shardbound.api.domain.bo.game.SeatUpdate;
import fr.daliush.shardbound.api.domain.bo.view.GameView;
import fr.daliush.shardbound.api.domain.bo.view.StateView;
import fr.daliush.shardbound.api.domain.bo.view.UpdateView;
import fr.daliush.shardbound.api.domain.mappers.view.EventViewMapper;
import fr.daliush.shardbound.api.domain.mappers.view.GameViewMapper;
import fr.daliush.shardbound.api.domain.mappers.view.SeatSnapshotMapper;
import fr.daliush.shardbound.api.domain.ports.GameNotificationPort;
import fr.daliush.shardbound.api.domain.ports.GameSessionPort;
import fr.daliush.shardbound.api.domain.ports.GameWatcher;
import fr.daliush.shardbound.api.domain.ports.Watch;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * What a seat has to receive, and when: its full state, the updates it missed, read from the outbox, and the
 * notifications that a game moved or that a seat has a new connection, on any instance (spec §13.4).
 */
@Service
public class SeatUpdateService {

    private final Content content;
    private final GameSessionPort sessions;
    private final GameNotificationPort notifications;
    private final SeatSnapshotMapper snapshots;
    private final GameViewMapper views;
    private final EventViewMapper events;

    public SeatUpdateService(Content content, GameSessionPort sessions, GameNotificationPort notifications,
                             SeatSnapshotMapper snapshots, GameViewMapper views, EventViewMapper events) {
        this.content = content;
        this.sessions = sessions;
        this.notifications = notifications;
        this.snapshots = snapshots;
        this.views = views;
        this.events = events;
    }

    /** Everything the seat needs to draw the game from scratch, at the current version. */
    public StateView state(GameId game, PlayerId seat) {
        GameSession session = sessions.load(game);
        if (session.status() == GameStatus.WAITING_FOR_OPPONENT) {
            Seat.Human creator = (Seat.Human) session.seat(seat);
            return new StateView(views.waiting(game, content.deck(creator.deck().value())), List.of());
        }
        GameView view = views.toView(game, session.version(),
                snapshots.toSnapshot(session.state().orElseThrow(), seat));
        return new StateView(view, events.toViews(session.events(), seat));
    }

    /**
     * The seat's updates after version {@code sent}, in order: empty when there is nothing new or the game is
     * gone, absent when the outbox no longer reaches back that far and the full state must be sent instead.
     */
    public Optional<List<UpdateView>> since(GameId game, PlayerId seat, int sent) {
        Optional<GameSession> found = sessions.find(game).filter(session -> session.version() > sent);
        if (found.isEmpty()) {
            return Optional.of(List.of());
        }
        GameSession session = found.get();
        return session.outbox().after(sent, session.version(), seat)
                .map(updates -> updates.stream().map(update -> toView(game, update)).toList());
    }

    public Watch watch(GameId game, GameWatcher watcher) {
        return notifications.watch(game, watcher);
    }

    /** A new connection holds the seat: every instance closes the older ones. */
    public void announce(GameId game, PlayerId seat, String connectionId) {
        notifications.announce(game, seat, connectionId);
    }

    private UpdateView toView(GameId game, SeatUpdate update) {
        return new UpdateView(views.toView(game, update.version(), update.snapshot()),
                events.toViews(update.events(), update.seat()));
    }
}
