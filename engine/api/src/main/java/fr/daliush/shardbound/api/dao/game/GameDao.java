package fr.daliush.shardbound.api.dao.game;

import fr.daliush.shardbound.api.dao.entities.game.GameEntity;
import java.util.Optional;
import java.util.UUID;

/** Where games live between two messages. Implementations: {@code GameDaoInMemory}, later a database. */
public interface GameDao {

    void create(GameEntity game);

    Optional<GameEntity> find(UUID id);

    /**
     * Saves the game only if the stored one is still at {@code expectedVersion}: an optimistic lock.
     *
     * @throws VersionConflict when another save came first
     */
    void save(GameEntity game, int expectedVersion);

    void delete(UUID id);
}
