package fr.daliush.shardbound.api.adapter.notification;

import fr.daliush.shardbound.api.dao.notification.GameNotificationDao;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.ports.GameNotificationPort;
import fr.daliush.shardbound.api.domain.ports.GameWatcher;
import fr.daliush.shardbound.api.domain.ports.Watch;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The domain's notification port, implemented on the notification DAO. */
@Component
public class GameNotificationAdapter implements GameNotificationPort {

    private final GameNotificationDao dao;

    public GameNotificationAdapter(GameNotificationDao dao) {
        this.dao = dao;
    }

    @Override
    public void publish(GameId game, int version) {
        dao.publish(game.value(), version);
    }

    @Override
    public void announce(GameId game, PlayerId seat, String connectionId) {
        dao.connected(game.value(), seat, connectionId);
    }

    @Override
    public Watch watch(GameId game, GameWatcher watcher) {
        return dao.subscribe(game.value(), new Forwarding(game, watcher))::cancel;
    }

    /** The DAO's notifications, passed on to the domain's watcher. */
    private record Forwarding(GameId game, GameWatcher watcher) implements GameNotificationDao.Listener {

        @Override
        public void updated(UUID id, int version) {
            watcher.moved(game, version);
        }

        @Override
        public void connected(UUID id, PlayerId seat, String connectionId) {
            watcher.seatTaken(game, seat, connectionId);
        }
    }
}
