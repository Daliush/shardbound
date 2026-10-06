package fr.daliush.shardbound.api.rest;

import fr.daliush.shardbound.api.session.GameId;
import fr.daliush.shardbound.api.session.GameSessionService;
import fr.daliush.shardbound.api.session.SessionException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Creating a game takes seat P1; joining one takes seat P2. The game itself is played over the WebSocket. */
@RestController
@RequestMapping("/api/games")
class GamesController {

    private final GameSessionService sessions;

    GamesController(GameSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GameRequests.Access create(@Valid @RequestBody GameRequests.Create request) {
        return GameRequests.Access.of(sessions.create(request.toNewGame()));
    }

    @PostMapping("/{id}/join")
    GameRequests.Access join(@PathVariable String id, @Valid @RequestBody GameRequests.Join request) {
        GameId game = GameId.parse(id).orElseThrow(() -> new SessionException.UnknownGame(id));
        return GameRequests.Access.of(sessions.join(game, request.joinCode(), request.deck()));
    }
}
