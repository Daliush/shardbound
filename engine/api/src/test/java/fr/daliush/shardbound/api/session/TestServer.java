package fr.daliush.shardbound.api.session;

import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.ContentLoader;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.random.SplitMix64;
import fr.daliush.shardbound.core.rules.GameEngine;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/** One instance of the session server on shared adapters, plus what tests need to play through it. */
final class TestServer {

    static final Content CONTENT = ContentLoader.load(ContentLoader.find(Path.of("")).orElseThrow());
    static final GameEngine ENGINE = new GameEngine(CONTENT.catalog());

    final GameRepository repository;
    final GameSessionService sessions;
    final PlayerConnections connections;

    TestServer(GameRepository repository, GameUpdates updates, String instance) {
        this.repository = repository;
        this.sessions = new GameSessionService(CONTENT, ENGINE, new BotRoster(), repository, updates,
                Clock.systemUTC(), new GameSessionService.Settings(Duration.ZERO, instance));
        this.connections = new PlayerConnections(sessions, updates);
    }

    GameSession session(GameId id) {
        return repository.find(id).orElseThrow();
    }

    Optional<Decision> decision(GameId id) {
        return ENGINE.decision(session(id).state().orElseThrow());
    }

    /** A random legal index, like a client clicking any button. */
    static int anyIndex(Decision decision, SplitMix64 random) {
        return random.nextInt(decision.actions().size());
    }
}
