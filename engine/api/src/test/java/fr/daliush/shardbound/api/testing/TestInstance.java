package fr.daliush.shardbound.api.testing;

import fr.daliush.shardbound.api.adapter.game.GameSessionAdapter;
import fr.daliush.shardbound.api.adapter.mappers.game.GameEntityMapper;
import fr.daliush.shardbound.api.adapter.notification.GameNotificationAdapter;
import fr.daliush.shardbound.api.controller.mappers.ws.ProtocolJson;
import fr.daliush.shardbound.api.controller.mappers.ws.ServerMessageMapper;
import fr.daliush.shardbound.api.controller.ws.GameSocketRegistry;
import fr.daliush.shardbound.api.controller.ws.SeatSocket;
import fr.daliush.shardbound.api.dao.game.GameDao;
import fr.daliush.shardbound.api.dao.game.GameDaoInMemory;
import fr.daliush.shardbound.api.dao.notification.GameNotificationDao;
import fr.daliush.shardbound.api.dao.notification.GameNotificationDaoInMemory;
import fr.daliush.shardbound.api.domain.bo.command.NewGame;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.game.GameSession;
import fr.daliush.shardbound.api.domain.mappers.view.DecisionViewMapper;
import fr.daliush.shardbound.api.domain.mappers.view.EventViewMapper;
import fr.daliush.shardbound.api.domain.mappers.view.GameViewMapper;
import fr.daliush.shardbound.api.domain.mappers.view.SeatSnapshotMapper;
import fr.daliush.shardbound.api.domain.ports.GameNotificationPort;
import fr.daliush.shardbound.api.domain.ports.GameSessionPort;
import fr.daliush.shardbound.api.domain.services.bot.BotRoster;
import fr.daliush.shardbound.api.domain.services.game.BotTurnService;
import fr.daliush.shardbound.api.domain.services.game.GameCreationService;
import fr.daliush.shardbound.api.domain.services.game.GameLocks;
import fr.daliush.shardbound.api.domain.services.game.GamePlayService;
import fr.daliush.shardbound.api.domain.services.game.GameSaver;
import fr.daliush.shardbound.api.domain.services.game.GameSettings;
import fr.daliush.shardbound.api.domain.services.game.SeatAuthenticationService;
import fr.daliush.shardbound.api.domain.services.security.SeatTokens;
import fr.daliush.shardbound.api.domain.services.update.SeatUpdateService;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.ContentLoader;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.random.SplitMix64;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.state.PlayerId;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * One server instance, wired by hand on DAOs it may share with other instances, as on Cloud Run (spec §13.4).
 * Building it without Spring also proves every layer takes its collaborators through its constructor.
 */
public final class TestInstance {

    public static final Content CONTENT = ContentLoader.load(ContentLoader.find(Path.of("")).orElseThrow());
    public static final GameEngine ENGINE = new GameEngine(CONTENT.catalog());

    public final GameSessionPort sessions;
    public final GameCreationService creation;
    public final GamePlayService play;
    public final SeatAuthenticationService authentication;
    public final SeatUpdateService seatUpdates;
    public final GameSocketRegistry sockets;
    private final ProtocolJson json = new ProtocolJson();

    public TestInstance(GameDao games, GameNotificationDao notifications, String name) {
        sessions = new GameSessionAdapter(games, new GameEntityMapper());
        GameNotificationPort notificationPort = new GameNotificationAdapter(notifications);
        GameSettings settings = new GameSettings(Duration.ZERO, name);
        Clock clock = Clock.systemUTC();
        SeatSnapshotMapper snapshots = new SeatSnapshotMapper(ENGINE);
        GameSaver saver = new GameSaver(ENGINE, sessions, notificationPort, snapshots, clock, settings);
        GameLocks locks = new GameLocks();
        BotRoster roster = new BotRoster(ENGINE);
        SeatTokens tokens = new SeatTokens();
        BotTurnService botTurns = new BotTurnService(ENGINE, sessions, saver, roster, locks, settings);
        play = new GamePlayService(ENGINE, sessions, saver, locks, botTurns);
        creation = new GameCreationService(CONTENT, ENGINE, sessions, saver, locks, botTurns, roster, tokens, clock);
        authentication = new SeatAuthenticationService(sessions, tokens);
        seatUpdates = new SeatUpdateService(CONTENT, sessions, notificationPort, snapshots,
                new GameViewMapper(ENGINE, new DecisionViewMapper()), new EventViewMapper(ENGINE));
        sockets = new GameSocketRegistry(seatUpdates, new ServerMessageMapper());
    }

    /** An instance with DAOs of its own. */
    public static TestInstance alone() {
        return new TestInstance(new GameDaoInMemory(), new GameNotificationDaoInMemory(), "a");
    }

    public static NewGame botGame(long seed) {
        return botGame(seed, "random");
    }

    public static NewGame botGame(long seed, String bot) {
        return new NewGame("ember-starter", new NewGame.Opponent.Bot(bot, "root-starter"), OptionalLong.of(seed));
    }

    public static NewGame humanGame(long seed) {
        return new NewGame("ember-starter", new NewGame.Opponent.Human(), OptionalLong.of(seed));
    }

    /** A random legal index, like a client clicking any button. */
    public static int anyIndex(Decision decision, SplitMix64 random) {
        return random.nextInt(decision.actions().size());
    }

    public GameSession session(GameId id) {
        return sessions.load(id);
    }

    public Optional<Decision> decision(GameId id) {
        return ENGINE.decision(session(id).state().orElseThrow());
    }

    /** A seat's socket on this instance, over a fake WebSocket that keeps what it is sent. */
    public SeatSocket socket(GameId game, PlayerId seat, FakeWebSocketSession session) {
        return new SeatSocket(game, seat, session, json);
    }
}
