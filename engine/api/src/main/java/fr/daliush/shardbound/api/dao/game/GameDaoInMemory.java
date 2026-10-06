package fr.daliush.shardbound.api.dao.game;

import fr.daliush.shardbound.api.dao.entities.game.GameEntity;
import fr.daliush.shardbound.core.json.GameJson;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * Games in this process's memory, for local runs and tests only: never behind several instances. Each game is
 * kept as a database row would be, its version and status beside its JSON, so every run exercises the
 * serialization a shared store needs.
 */
@Repository
public class GameDaoInMemory implements GameDao {

    private record Row(int version, String status, Instant lastActivity, String json) {}

    private static final String FINISHED = "FINISHED";

    private final Map<UUID, Row> rows = new ConcurrentHashMap<>();
    private final GameJson json = new GameJson();

    @Override
    public void create(GameEntity game) {
        if (rows.putIfAbsent(game.id(), row(game)) != null) {
            throw new IllegalStateException("Game " + game.id() + " already exists");
        }
    }

    @Override
    public Optional<GameEntity> find(UUID id) {
        return Optional.ofNullable(rows.get(id)).map(row -> json.readValue(row.json(), GameEntity.class));
    }

    @Override
    public void save(GameEntity game, int expectedVersion) {
        Row saved = row(game);
        rows.compute(game.id(), (id, stored) -> {
            if (stored == null) {
                throw new IllegalStateException("Game " + id + " does not exist");
            }
            if (stored.version() != expectedVersion) {
                throw new VersionConflict(id, expectedVersion, stored.version());
            }
            return saved;
        });
    }

    @Override
    public void delete(UUID id) {
        rows.remove(id);
    }

    /** Drops finished games after {@code finishedTtl}, and any game without activity for {@code idleTtl}. */
    public void evictExpired(Instant now, Duration finishedTtl, Duration idleTtl) {
        rows.values().removeIf(row -> {
            Duration idle = Duration.between(row.lastActivity(), now);
            return idle.compareTo(idleTtl) > 0 || row.status().equals(FINISHED) && idle.compareTo(finishedTtl) > 0;
        });
    }

    private Row row(GameEntity game) {
        return new Row(game.version(), game.status(), game.lastActivity(), json.writeValue(game));
    }
}
