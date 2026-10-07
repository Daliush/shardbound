package fr.daliush.shardbound.api.controller.rest;

import fr.daliush.shardbound.api.controller.mappers.rest.GameRequestMapper;
import fr.daliush.shardbound.api.controller.rest.dto.CreateGameRequest;
import fr.daliush.shardbound.api.controller.rest.dto.JoinGameRequest;
import fr.daliush.shardbound.api.controller.rest.dto.SeatAccessResponse;
import fr.daliush.shardbound.api.domain.bo.error.GameException;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.services.game.GameCreationService;
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
public class GameController {

    private final GameCreationService games;
    private final GameRequestMapper mapper;

    public GameController(GameCreationService games, GameRequestMapper mapper) {
        this.games = games;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SeatAccessResponse create(@Valid @RequestBody CreateGameRequest request) {
        return mapper.toResponse(games.create(mapper.toNewGame(request)));
    }

    @PostMapping("/{id}/join")
    public SeatAccessResponse join(@PathVariable String id, @Valid @RequestBody JoinGameRequest request) {
        GameId game = GameId.parse(id).orElseThrow(() -> new GameException.UnknownGame(id));
        return mapper.toResponse(games.join(game, request.joinCode(), request.deck()));
    }
}
