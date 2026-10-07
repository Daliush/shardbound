package fr.daliush.shardbound.api.dao.notification;

import fr.daliush.shardbound.core.state.PlayerId;
import java.util.UUID;

/**
 * Carries notifications between instances: a game moved, or a seat has a new connection. A notification carries
 * no message: whoever listens reads what it needs from the stored game. Implementations:
 * {@code GameNotificationDaoInMemory}, later the shared store's pub/sub.
 */
public interface GameNotificationDao {

    /** The game is now at {@code version}. */
    void publish(UUID game, int version);

    /** A new connection holds the seat: older ones, on any instance, must close. */
    void connected(UUID game, PlayerId seat, String connectionId);

    Subscription subscribe(UUID game, Listener listener);

    /** Called on the instance that subscribed, whichever instance published. */
    interface Listener {

        void updated(UUID game, int version);

        void connected(UUID game, PlayerId seat, String connectionId);
    }

    interface Subscription {

        void cancel();
    }
}
