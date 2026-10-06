package fr.daliush.shardbound.api.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fr.daliush.shardbound.api.session.NewGame;
import fr.daliush.shardbound.api.session.SeatAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.OptionalLong;

/** The bodies of {@code POST /api/games} and {@code POST /api/games/{id}/join}, and their answer. */
final class GameRequests {

    private GameRequests() {
    }

    record Create(@NotBlank String deck, @NotNull @Valid Opponent opponent, Long seed) {

        NewGame toNewGame() {
            return new NewGame(deck, opponent.toOpponent(), seed == null ? OptionalLong.empty() : OptionalLong.of(seed));
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Opponent.Bot.class, name = "bot"),
        @JsonSubTypes.Type(value = Opponent.Human.class, name = "human")
    })
    sealed interface Opponent {

        NewGame.Opponent toOpponent();

        record Bot(@NotBlank String bot, @NotBlank String deck) implements Opponent {
            @Override
            public NewGame.Opponent toOpponent() {
                return new NewGame.Opponent.Bot(bot, deck);
            }
        }

        record Human() implements Opponent {
            @Override
            public NewGame.Opponent toOpponent() {
                return new NewGame.Opponent.Human();
            }
        }
    }

    record Join(@NotBlank String joinCode, @NotBlank String deck) {}

    /** {@code joinCode} only for the creator of a game against a human. The seed is never returned. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Access(String gameId, String playerToken, String joinCode, String websocketPath) {

        static Access of(SeatAccess access) {
            String id = access.game().toString();
            return new Access(id, access.playerToken(), access.joinCode().orElse(null), "/ws/games/" + id);
        }
    }
}
