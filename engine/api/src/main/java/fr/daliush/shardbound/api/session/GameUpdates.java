package fr.daliush.shardbound.api.session;

import fr.daliush.shardbound.core.state.PlayerId;

/**
 * Tells every instance that a game moved. A notification carries no message: listeners read what they have
 * not sent yet from the session's outbox. Implementations: {@code GameUpdatesInMemory}, later a shared pub/sub.
 */
public interface GameUpdates {

    /** The game is now at {@code version}. */
    void publish(GameId game, int version);

    /** A new connection holds the seat: older ones, on any instance, must close (spec §13.1). */
    void connected(GameId game, PlayerId seat, String connectionId);

    Subscription subscribe(GameId game, Listener listener);

    interface Listener {

        void updated(GameId game, int version);

        void connected(GameId game, PlayerId seat, String connectionId);
    }

    interface Subscription {

        void cancel();
    }
}
