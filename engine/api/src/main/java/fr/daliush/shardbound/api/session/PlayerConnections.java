package fr.daliush.shardbound.api.session;

import fr.daliush.shardbound.core.state.PlayerId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * This instance's connections, and only them (spec §13.4). It listens to {@link GameUpdates} for every game it
 * holds a connection to, sends each connection the updates it has not received yet, in order, from the
 * session's outbox, and closes a connection that a newer one replaced, wherever the newer one is.
 */
public final class PlayerConnections implements GameUpdates.Listener {

    private final GameSessionService sessions;
    private final GameUpdates updates;
    private final ReentrantLock registry = new ReentrantLock();
    private final Map<GameId, List<Held>> held = new HashMap<>();
    private final Map<GameId, GameUpdates.Subscription> subscriptions = new HashMap<>();

    public PlayerConnections(GameSessionService sessions, GameUpdates updates) {
        this.sessions = sessions;
        this.updates = updates;
    }

    /** Registers the connection before reading the state, so no update falls between the two. */
    public void open(GameId game, PlayerId seat, SeatConnection connection) {
        Held entry = new Held(seat, connection);
        register(game, entry);
        updates.connected(game, seat, connection.id());
        entry.sendState(() -> sessions.state(game, seat));
        sessions.resumeBots(game);
    }

    /** The connection asked for the full state, after a gap in versions or a reconnection. */
    public void sync(GameId game, SeatConnection connection) {
        find(game, connection).ifPresent(entry -> entry.sendState(() -> sessions.state(game, entry.seat)));
        sessions.resumeBots(game);
    }

    public void closed(GameId game, SeatConnection connection) {
        find(game, connection).ifPresent(entry -> unregister(game, entry));
    }

    @Override
    public void updated(GameId game, int version) {
        for (Held entry : connectionsTo(game)) {
            deliver(game, entry);
        }
    }

    @Override
    public void connected(GameId game, PlayerId seat, String connectionId) {
        for (Held entry : connectionsTo(game)) {
            if (entry.seat == seat && !entry.connection.id().equals(connectionId)) {
                unregister(game, entry);
                entry.connection.replaced();
            }
        }
    }

    /** Sends what the connection has not received yet: the outbox's updates, or the state if it is too far behind. */
    private void deliver(GameId game, Held entry) {
        entry.lock.lock();
        try {
            if (!entry.hasState()) {
                return;
            }
            sessions.find(game).filter(session -> session.version() > entry.sent).ifPresent(session -> {
                session.outbox().after(entry.sent, session.version(), entry.seat).ifPresentOrElse(
                        pending -> pending.forEach(entry.connection::send),
                        () -> entry.connection.send(sessions.state(session, entry.seat)));
                entry.sent = session.version();
            });
        } finally {
            entry.lock.unlock();
        }
    }

    private void register(GameId game, Held entry) {
        registry.lock();
        try {
            held.computeIfAbsent(game, id -> new ArrayList<>()).add(entry);
            subscriptions.computeIfAbsent(game, id -> updates.subscribe(id, this));
        } finally {
            registry.unlock();
        }
    }

    private void unregister(GameId game, Held entry) {
        registry.lock();
        try {
            List<Held> entries = held.getOrDefault(game, List.of());
            entries.remove(entry);
            if (entries.isEmpty()) {
                held.remove(game);
                Optional.ofNullable(subscriptions.remove(game)).ifPresent(GameUpdates.Subscription::cancel);
            }
        } finally {
            registry.unlock();
        }
    }

    private List<Held> connectionsTo(GameId game) {
        registry.lock();
        try {
            return List.copyOf(held.getOrDefault(game, List.of()));
        } finally {
            registry.unlock();
        }
    }

    private Optional<Held> find(GameId game, SeatConnection connection) {
        return connectionsTo(game).stream().filter(entry -> entry.connection == connection).findFirst();
    }

    /** A connection, its seat, and the last version it was sent. */
    private static final class Held {
        private final PlayerId seat;
        private final SeatConnection connection;
        private final ReentrantLock lock = new ReentrantLock();
        private int sent = -1;

        Held(PlayerId seat, SeatConnection connection) {
            this.seat = seat;
            this.connection = connection;
        }

        boolean hasState() {
            return sent >= 0;
        }

        /** Reads the state under the lock: an update saved meanwhile waits, then goes out after it. */
        void sendState(Supplier<SeatState> read) {
            lock.lock();
            try {
                SeatState state = read.get();
                connection.send(state.message());
                sent = state.version();
            } finally {
                lock.unlock();
            }
        }
    }
}
