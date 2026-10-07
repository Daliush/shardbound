package fr.daliush.shardbound.api.controller.rest.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** The body of {@code POST /api/games}. {@code seed} is optional. */
public record CreateGameRequest(@NotBlank String deck, @NotNull @Valid Opponent opponent, Long seed) {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Opponent.Bot.class, name = "bot"),
        @JsonSubTypes.Type(value = Opponent.Human.class, name = "human")
    })
    public sealed interface Opponent {

        record Bot(@NotBlank String bot, @NotBlank String deck) implements Opponent {}

        record Human() implements Opponent {}
    }
}
