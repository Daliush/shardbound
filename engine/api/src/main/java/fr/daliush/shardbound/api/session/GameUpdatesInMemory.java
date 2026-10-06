package fr.daliush.shardbound.api.session;

import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Notifications inside this process, delivered on the publishing thread. For local runs and tests only. */
public final class GameUpdatesInMemory implements GameUpdates {

    private final Map<GameId, List<Listener>> listeners = new ConcurrentHashMap<>();

    @Override
    public void publish(GameId game, int version) {
        listeners.getOrDefault(game, List.of()).forEach(listener -> listener.updated(game, version));
    }

    @Override
    public void connected(GameId game, PlayerId seat, String connectionId) {
        listeners.getOrDefault(game, List.of()).forEach(listener -> listener.connected(game, seat, connectionId));
    }

    @Override
    public Subscription subscribe(GameId game, Listener listener) {
        listeners.computeIfAbsent(game, id -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> listeners.computeIfPresent(game, (id, list) -> {
            list.remove(listener);
            return list.isEmpty() ? null : list;
        });
    }
}
