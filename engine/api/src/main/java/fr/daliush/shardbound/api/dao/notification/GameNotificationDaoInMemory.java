package fr.daliush.shardbound.api.dao.notification;

import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Notifications inside this process, delivered on the publishing thread. For local runs and tests only.
 * A listener that fails is logged: it never reaches the publisher, nor the other listeners.
 */
@Repository
public class GameNotificationDaoInMemory implements GameNotificationDao {

    private static final Logger LOG = LoggerFactory.getLogger(GameNotificationDaoInMemory.class);

    private final Map<UUID, List<Listener>> listeners = new ConcurrentHashMap<>();

    @Override
    public void publish(UUID game, int version) {
        listenersOf(game).forEach(listener -> notify(game, () -> listener.updated(game, version)));
    }

    @Override
    public void connected(UUID game, PlayerId seat, String connectionId) {
        listenersOf(game).forEach(listener -> notify(game, () -> listener.connected(game, seat, connectionId)));
    }

    @Override
    public Subscription subscribe(UUID game, Listener listener) {
        listeners.computeIfAbsent(game, id -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> listeners.computeIfPresent(game, (id, list) -> {
            list.remove(listener);
            return list.isEmpty() ? null : list;
        });
    }

    private List<Listener> listenersOf(UUID game) {
        return listeners.getOrDefault(game, List.of());
    }

    private static void notify(UUID game, Runnable call) {
        try {
            call.run();
        } catch (RuntimeException e) {
            LOG.error("A listener of game {} failed", game, e);
        }
    }
}
