package fr.daliush.shardbound.api.controller.ws;

import fr.daliush.shardbound.api.controller.mappers.ws.ServerMessageMapper;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.view.StateView;
import fr.daliush.shardbound.api.domain.bo.view.UpdateView;
import fr.daliush.shardbound.api.domain.ports.GameWatcher;
import fr.daliush.shardbound.api.domain.ports.Watch;
import fr.daliush.shardbound.api.domain.services.update.SeatUpdateService;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/**
 * This instance's game sockets, and only them (spec §13.4). It watches every game it holds a socket to, sends
 * each socket the updates it has not received, in order, and closes a socket that a newer one replaced,
 * wherever the newer one is.
 */
@Component
public class GameSocketRegistry implements GameWatcher {

    private final SeatUpdateService seatUpdates;
    private final ServerMessageMapper messages;
    private final ReentrantLock registry = new ReentrantLock();
    private final Map<GameId, List<Entry>> entries = new HashMap<>();
    private final Map<GameId, Watch> watches = new HashMap<>();

    public GameSocketRegistry(SeatUpdateService seatUpdates, ServerMessageMapper messages) {
        this.seatUpdates = seatUpdates;
        this.messages = messages;
    }

    /** Registers the socket before reading the state, so no update falls between the two. */
    public void open(SeatSocket socket) {
        Entry entry = new Entry(socket);
        register(entry);
        seatUpdates.announce(socket.game(), socket.seat(), socket.id());
        sendState(entry);
    }

    /** The client asked for the full state, after a gap in versions. */
    public void sync(SeatSocket socket) {
        find(socket).ifPresent(this::sendState);
    }

    public void closed(SeatSocket socket) {
        find(socket).ifPresent(this::unregister);
    }

    @Override
    public void moved(GameId game, int version) {
        entriesOf(game).forEach(this::deliver);
    }

    @Override
    public void seatTaken(GameId game, PlayerId seat, String connectionId) {
        for (Entry entry : entriesOf(game)) {
            if (entry.socket.seat() == seat && !entry.socket.id().equals(connectionId)) {
                unregister(entry);
                entry.socket.close(SeatSocket.REPLACED);
            }
        }
    }

    /** Sends the updates the socket has not received, or the full state if the outbox no longer has them. */
    private void deliver(Entry entry) {
        entry.lock.lock();
        try {
            if (!entry.hasState()) {
                return;
            }
            Optional<List<UpdateView>> missed = seatUpdates.since(entry.socket.game(), entry.socket.seat(),
                    entry.sent);
            if (missed.isEmpty()) {
                sendState(entry);
                return;
            }
            for (UpdateView update : missed.get()) {
                entry.socket.send(messages.update(update));
                entry.sent = update.version();
            }
        } finally {
            entry.lock.unlock();
        }
    }

    /** Reads the state under the socket's lock: an update saved meanwhile waits, then goes out after it. */
    private void sendState(Entry entry) {
        entry.lock.lock();
        try {
            StateView state = seatUpdates.state(entry.socket.game(), entry.socket.seat());
            entry.socket.send(messages.state(state));
            entry.sent = state.version();
        } finally {
            entry.lock.unlock();
        }
    }

    private void register(Entry entry) {
        GameId game = entry.socket.game();
        registry.lock();
        try {
            entries.computeIfAbsent(game, id -> new ArrayList<>()).add(entry);
            watches.computeIfAbsent(game, id -> seatUpdates.watch(id, this));
        } finally {
            registry.unlock();
        }
    }

    private void unregister(Entry entry) {
        GameId game = entry.socket.game();
        registry.lock();
        try {
            List<Entry> ofGame = entries.getOrDefault(game, List.of());
            ofGame.remove(entry);
            if (ofGame.isEmpty()) {
                entries.remove(game);
                Optional.ofNullable(watches.remove(game)).ifPresent(Watch::stop);
            }
        } finally {
            registry.unlock();
        }
    }

    private List<Entry> entriesOf(GameId game) {
        registry.lock();
        try {
            return List.copyOf(entries.getOrDefault(game, List.of()));
        } finally {
            registry.unlock();
        }
    }

    private Optional<Entry> find(SeatSocket socket) {
        return entriesOf(socket.game()).stream().filter(entry -> entry.socket == socket).findFirst();
    }

    /** A socket, and the last version it was sent; -1 until its state has gone out. */
    private static final class Entry {
        private final SeatSocket socket;
        private final ReentrantLock lock = new ReentrantLock();
        private int sent = -1;

        Entry(SeatSocket socket) {
            this.socket = socket;
        }

        boolean hasState() {
            return sent >= 0;
        }
    }
}
