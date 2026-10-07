package fr.daliush.shardbound.api.controller.mappers.rest;

import fr.daliush.shardbound.api.controller.rest.dto.CreateGameRequest;
import fr.daliush.shardbound.api.controller.rest.dto.SeatAccessResponse;
import fr.daliush.shardbound.api.domain.bo.command.NewGame;
import fr.daliush.shardbound.api.domain.bo.command.SeatAccess;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/** Creating and joining games: the request to the domain's command, the seat access back to the response. */
@Component
public class GameRequestMapper {

    public NewGame toNewGame(CreateGameRequest request) {
        NewGame.Opponent opponent = switch (request.opponent()) {
            case CreateGameRequest.Opponent.Bot bot -> new NewGame.Opponent.Bot(bot.bot(), bot.deck());
            case CreateGameRequest.Opponent.Human ignored -> new NewGame.Opponent.Human();
        };
        OptionalLong seed = request.seed() == null ? OptionalLong.empty() : OptionalLong.of(request.seed());
        return new NewGame(request.deck(), opponent, seed);
    }

    public SeatAccessResponse toResponse(SeatAccess access) {
        String id = access.game().toString();
        return new SeatAccessResponse(id, access.playerToken(), access.joinCode().orElse(null), "/ws/games/" + id);
    }
}
