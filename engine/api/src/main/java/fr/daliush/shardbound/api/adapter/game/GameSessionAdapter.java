package fr.daliush.shardbound.api.adapter.game;

import fr.daliush.shardbound.api.adapter.mappers.game.GameEntityMapper;
import fr.daliush.shardbound.api.dao.game.GameDao;
import fr.daliush.shardbound.api.dao.game.VersionConflict;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.game.GameSession;
import fr.daliush.shardbound.api.domain.ports.GameSessionPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Game sessions through the game DAO: business objects in, entities stored, business objects out. */
@Component
public class GameSessionAdapter implements GameSessionPort {

    private final GameDao dao;
    private final GameEntityMapper mapper;

    public GameSessionAdapter(GameDao dao, GameEntityMapper mapper) {
        this.dao = dao;
        this.mapper = mapper;
    }

    @Override
    public void create(GameSession session) {
        dao.create(mapper.toEntity(session));
    }

    @Override
    public Optional<GameSession> find(GameId id) {
        return dao.find(id.value()).map(mapper::toSession);
    }

    @Override
    public boolean save(GameSession session, int expectedVersion) {
        try {
            dao.save(mapper.toEntity(session), expectedVersion);
            return true;
        } catch (VersionConflict conflict) {
            return false;
        }
    }
}
