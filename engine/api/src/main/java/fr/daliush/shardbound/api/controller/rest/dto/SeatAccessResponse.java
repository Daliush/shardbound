package fr.daliush.shardbound.api.controller.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** {@code joinCode} only for the creator of a game against a human. The seed is never returned. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeatAccessResponse(String gameId, String playerToken, String joinCode, String websocketPath) {
}
