package fr.daliush.shardbound.api.ws;

import fr.daliush.shardbound.api.session.GameId;
import fr.daliush.shardbound.api.session.GameSessionService;
import fr.daliush.shardbound.api.session.SessionException;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/** {@code /ws/games/{id}?token=…}: finds the game and the seat the token holds, or refuses with 404 or 401. */
final class GameHandshakeInterceptor implements HandshakeInterceptor {

    static final String GAME = "shardbound.game";
    static final String SEAT = "shardbound.seat";

    private final GameSessionService sessions;

    GameHandshakeInterceptor(GameSessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        String path = request.getURI().getPath();
        String id = path.substring(path.lastIndexOf('/') + 1);
        String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
        try {
            GameId game = GameId.parse(id).orElseThrow(() -> new SessionException.UnknownGame(id));
            PlayerId seat = sessions.authenticate(game, token);
            attributes.put(GAME, game);
            attributes.put(SEAT, seat);
            return true;
        } catch (SessionException.UnknownGame unknown) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
        } catch (SessionException.InvalidToken invalid) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                               Exception exception) {
    }
}
