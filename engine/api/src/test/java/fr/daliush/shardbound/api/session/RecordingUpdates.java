package fr.daliush.shardbound.api.session;

import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory notifications that also remember every version published, per game. */
final class RecordingUpdates implements GameUpdates {

    private final GameUpdatesInMemory delegate = new GameUpdatesInMemory();
    private final List<Published> published = new CopyOnWriteArrayList<>();

    record Published(GameId game, int version) {}

    List<Integer> versions(GameId game) {
        return published.stream().filter(p -> p.game().equals(game)).map(Published::version).toList();
    }

    @Override
    public void publish(GameId game, int version) {
        published.add(new Published(game, version));
        delegate.publish(game, version);
    }

    @Override
    public void connected(GameId game, PlayerId seat, String connectionId) {
        delegate.connected(game, seat, connectionId);
    }

    @Override
    public Subscription subscribe(GameId game, Listener listener) {
        return delegate.subscribe(game, listener);
    }
}
