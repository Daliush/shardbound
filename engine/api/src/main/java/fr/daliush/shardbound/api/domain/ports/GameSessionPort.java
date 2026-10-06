package fr.daliush.shardbound.api.domain.ports;

import fr.daliush.shardbound.api.domain.bo.error.GameException;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.game.GameSession;
import java.util.Optional;

/** What the domain needs to keep game sessions between two messages, whatever stores them. */
public interface GameSessionPort {

    void create(GameSession session);

    Optional<GameSession> find(GameId id);

    /** Saves the session if the stored one is still at {@code expectedVersion}; false when another save came first. */
    boolean save(GameSession session, int expectedVersion);

    default GameSession load(GameId id) {
        return find(id).orElseThrow(() -> new GameException.UnknownGame(id.toString()));
    }
}
