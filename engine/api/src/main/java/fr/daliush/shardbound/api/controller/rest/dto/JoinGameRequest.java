package fr.daliush.shardbound.api.controller.rest.dto;

import jakarta.validation.constraints.NotBlank;

/** The body of {@code POST /api/games/{id}/join}. */
public record JoinGameRequest(@NotBlank String joinCode, @NotBlank String deck) {
}
