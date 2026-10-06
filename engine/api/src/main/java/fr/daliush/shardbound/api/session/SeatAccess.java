package fr.daliush.shardbound.api.session;

import java.util.Optional;

/** What a player needs to take their seat: the game, their secret token, and the code to invite a human. */
public record SeatAccess(GameId game, String playerToken, Optional<String> joinCode) {
}
