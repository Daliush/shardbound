package fr.daliush.shardbound.api.testing;

import fr.daliush.shardbound.api.dao.notification.GameNotificationDao;
import fr.daliush.shardbound.api.dao.notification.GameNotificationDaoInMemory;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory notifications that also remember every version published, per game. */
public final class RecordingNotificationDao implements GameNotificationDao {

    private final GameNotificationDaoInMemory delegate = new GameNotificationDaoInMemory();
    private final List<Published> published = new CopyOnWriteArrayList<>();

    private record Published(UUID game, int version) {}

    public List<Integer> versions(UUID game) {
        return published.stream().filter(p -> p.game().equals(game)).map(Published::version).toList();
    }

    @Override
    public void publish(UUID game, int version) {
        published.add(new Published(game, version));
        delegate.publish(game, version);
    }

    @Override
    public void connected(UUID game, PlayerId seat, String connectionId) {
        delegate.connected(game, seat, connectionId);
    }

    @Override
    public Subscription subscribe(UUID game, Listener listener) {
        return delegate.subscribe(game, listener);
    }
}
