package fr.daliush.shardbound.api.config;

import fr.daliush.shardbound.api.controller.ws.GameHandshakeInterceptor;
import fr.daliush.shardbound.api.controller.ws.GameWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** The game protocol's endpoint: {@code ws://host/ws/games/{id}?token=…} (spec §13.1). */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler handler;
    private final GameHandshakeInterceptor handshake;
    private final ShardboundProperties properties;

    WebSocketConfig(GameWebSocketHandler handler, GameHandshakeInterceptor handshake,
                    ShardboundProperties properties) {
        this.handler = handler;
        this.handshake = handshake;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/games/*")
                .addInterceptors(handshake)
                .setAllowedOriginPatterns(properties.websocket().allowedOrigins().toArray(String[]::new));
    }
}
