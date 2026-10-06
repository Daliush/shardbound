package fr.daliush.shardbound.api.config;

import fr.daliush.shardbound.api.session.GameSessionService;
import fr.daliush.shardbound.api.session.PlayerConnections;
import fr.daliush.shardbound.api.ws.GameSockets;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** The game protocol's endpoint: {@code ws://host/ws/games/{id}?token=…} (spec §13.1). */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {

    private final GameSessionService sessions;
    private final PlayerConnections connections;
    private final ShardboundProperties properties;

    WebSocketConfig(GameSessionService sessions, PlayerConnections connections, ShardboundProperties properties) {
        this.sessions = sessions;
        this.connections = connections;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        GameSockets.register(registry, sessions, connections, properties.websocket().allowedOrigins());
    }
}
