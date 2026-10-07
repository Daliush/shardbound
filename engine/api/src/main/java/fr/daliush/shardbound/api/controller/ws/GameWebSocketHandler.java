package fr.daliush.shardbound.api.controller.ws;

import fr.daliush.shardbound.api.controller.mappers.ws.ProtocolJson;
import fr.daliush.shardbound.api.controller.mappers.ws.ServerMessageMapper;
import fr.daliush.shardbound.api.controller.ws.message.ClientMessage;
import fr.daliush.shardbound.api.domain.bo.command.Rejection;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.services.game.BotTurnService;
import fr.daliush.shardbound.api.domain.services.game.GamePlayService;
import fr.daliush.shardbound.core.state.PlayerId;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** The game protocol's endpoint (spec §13.1, §13.2): one handler for every game's sockets. */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final String SOCKET = "shardbound.socket";

    private final GameSocketRegistry registry;
    private final GamePlayService play;
    private final BotTurnService botTurns;
    private final ProtocolJson json;
    private final ServerMessageMapper messages;

    public GameWebSocketHandler(GameSocketRegistry registry, GamePlayService play, BotTurnService botTurns,
                                ProtocolJson json, ServerMessageMapper messages) {
        this.registry = registry;
        this.play = play;
        this.botTurns = botTurns;
        this.json = json;
        this.messages = messages;
    }

    /** A game left on a bot's decision, by an instance that stopped mid-turn, resumes here. */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SeatSocket socket = new SeatSocket((GameId) session.getAttributes().get(GameHandshakeInterceptor.GAME),
                (PlayerId) session.getAttributes().get(GameHandshakeInterceptor.SEAT), session, json);
        session.getAttributes().put(SOCKET, socket);
        socket.process(() -> {
            registry.open(socket);
            botTurns.resume(socket.game());
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SeatSocket socket = socket(session);
        socket.process(() -> answer(socket, message.getPayload()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SeatSocket socket = socket(session);
        socket.process(() -> registry.closed(socket));
        socket.shutdown();
    }

    private void answer(SeatSocket socket, String text) {
        switch (json.read(text).orElse(null)) {
            case ClientMessage.Act act when act.isComplete() -> play
                    .act(socket.game(), socket.seat(), act.decisionId(), act.action())
                    .ifPresent(rejection -> socket.send(messages.rejected(act.requestId(), rejection)));
            case ClientMessage.Act act -> socket.send(messages.rejected(act.requestId(),
                    malformed("An act needs a decisionId and an action index.")));
            case ClientMessage.Sync ignored -> {
                registry.sync(socket);
                botTurns.resume(socket.game());
            }
            case null -> socket.send(messages.rejected(null, malformed("Not a message of the game protocol.")));
        }
    }

    private static Rejection malformed(String message) {
        return new Rejection(Rejection.Reason.MALFORMED_MESSAGE, message);
    }

    private static SeatSocket socket(WebSocketSession session) {
        return (SeatSocket) session.getAttributes().get(SOCKET);
    }
}
