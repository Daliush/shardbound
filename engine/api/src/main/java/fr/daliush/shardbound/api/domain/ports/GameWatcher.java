package fr.daliush.shardbound.api.domain.ports;

import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.core.state.PlayerId;

/** Told on this instance when a watched game moves, whichever instance moved it. */
public interface GameWatcher {

    /** The game is now at {@code version}: ask the update service what is new. */
    void moved(GameId game, int version);

    /** A new connection took the seat, on any instance: older connections of that seat must close. */
    void seatTaken(GameId game, PlayerId seat, String connectionId);
}
