package fr.daliush.shardbound.api.domain.ports;

import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.core.state.PlayerId;

/**
 * What the domain needs to tell every instance that a game moved, or that a seat has a new connection, and to
 * hear it. A notification carries no message: the stored game holds what changed.
 */
public interface GameNotificationPort {

    /** The game is now at {@code version}. */
    void publish(GameId game, int version);

    /** A new connection holds the seat: older ones, on any instance, must close. */
    void announce(GameId game, PlayerId seat, String connectionId);

    /** The watcher is told on this instance, whichever instance published. */
    Watch watch(GameId game, GameWatcher watcher);
}
