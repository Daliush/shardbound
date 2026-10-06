package fr.daliush.shardbound.api.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Sessions in this process's memory: for local runs and tests only, never behind several instances. */
public final class GameRepositoryInMemory implements GameRepository {

    private final Map<GameId, GameSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void create(GameSession session) {
        if (sessions.putIfAbsent(session.id(), session) != null) {
            throw new IllegalStateException("Game " + session.id() + " already exists");
        }
    }

    @Override
    public Optional<GameSession> find(GameId id) {
        return Optional.ofNullable(sessions.get(id));
    }

    @Override
    public void save(GameSession session, int expectedVersion) {
        sessions.compute(session.id(), (id, stored) -> {
            if (stored == null) {
                throw new IllegalStateException("Game " + id + " does not exist");
            }
            if (stored.version() != expectedVersion) {
                throw new VersionConflict(id, expectedVersion, stored.version());
            }
            return session;
        });
    }

    @Override
    public void delete(GameId id) {
        sessions.remove(id);
    }

    /** Drops finished games after {@code finishedTtl}, and any game without activity for {@code idleTtl}. */
    public void evictExpired(Instant now, Duration finishedTtl, Duration idleTtl) {
        sessions.values().removeIf(session -> {
            Duration idle = Duration.between(session.lastActivity(), now);
            return idle.compareTo(idleTtl) > 0
                    || session.status() == GameStatus.FINISHED && idle.compareTo(finishedTtl) > 0;
        });
    }
}
