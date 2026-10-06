package fr.daliush.shardbound.api.session;

import java.util.Optional;

/** Where sessions live between two messages. Implementations: {@code GameRepositoryInMemory}, later a database. */
public interface GameRepository {

    void create(GameSession session);

    Optional<GameSession> find(GameId id);

    /**
     * Saves the session only if the stored one is still at {@code expectedVersion}: an optimistic lock.
     *
     * @throws VersionConflict when another save came first
     */
    void save(GameSession session, int expectedVersion);

    void delete(GameId id);
}
