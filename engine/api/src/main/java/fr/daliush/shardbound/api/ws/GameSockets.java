package fr.daliush.shardbound.api.ws;

import fr.daliush.shardbound.api.session.GameSessionService;
import fr.daliush.shardbound.api.session.PlayerConnections;
import java.util.List;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the game protocol's endpoint. */
public final class GameSockets {

    public static final String PATH = "/ws/games/*";

    private GameSockets() {
    }

    public static void register(WebSocketHandlerRegistry registry, GameSessionService sessions,
                                PlayerConnections connections, List<String> allowedOrigins) {
        registry.addHandler(new GameWebSocketHandler(sessions, connections), PATH)
                .addInterceptors(new GameHandshakeInterceptor(sessions))
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
    }
}
