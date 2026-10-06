package fr.daliush.shardbound.api.controller.ws;

import fr.daliush.shardbound.api.domain.bo.error.GameException;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.services.game.SeatAuthenticationService;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/** {@code /ws/games/{id}?token=…}: finds the game and the seat the token holds, or refuses with 404 or 401. */
@Component
public class GameHandshakeInterceptor implements HandshakeInterceptor {

    static final String GAME = "shardbound.game";
    static final String SEAT = "shardbound.seat";

    private final SeatAuthenticationService authentication;

    public GameHandshakeInterceptor(SeatAuthenticationService authentication) {
        this.authentication = authentication;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        String path = request.getURI().getPath();
        String id = path.substring(path.lastIndexOf('/') + 1);
        String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
        try {
            GameId game = GameId.parse(id).orElseThrow(() -> new GameException.UnknownGame(id));
            PlayerId seat = authentication.authenticate(game, token);
            attributes.put(GAME, game);
            attributes.put(SEAT, seat);
            return true;
        } catch (GameException.UnknownGame unknown) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
        } catch (GameException.InvalidToken invalid) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                               Exception exception) {
    }
}
