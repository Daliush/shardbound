package fr.daliush.shardbound.api.domain.services.game;

import fr.daliush.shardbound.api.domain.bo.error.GameException;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.game.GameSession;
import fr.daliush.shardbound.api.domain.bo.game.Seat;
import fr.daliush.shardbound.api.domain.ports.GameSessionPort;
import fr.daliush.shardbound.api.domain.services.security.SeatTokens;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/** Which seat a token holds: the token is hashed and compared with the human seats' hashes. */
@Service
public class SeatAuthenticationService {

    private final GameSessionPort sessions;
    private final SeatTokens tokens;

    public SeatAuthenticationService(GameSessionPort sessions, SeatTokens tokens) {
        this.sessions = sessions;
        this.tokens = tokens;
    }

    public PlayerId authenticate(GameId id, String token) {
        GameSession session = sessions.load(id);
        return Stream.of(session.p1(), session.p2())
                .filter(seat -> seat instanceof Seat.Human human && tokens.matches(token, human.tokenHash()))
                .map(Seat::player)
                .findFirst()
                .orElseThrow(GameException.InvalidToken::new);
    }
}
