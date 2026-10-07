package fr.daliush.shardbound.api.dao.entities.game;

import fr.daliush.shardbound.core.state.PlayerId;

/**
 * A stored seat. {@code kind} is "human", "bot" or "open", and only its own fields are set: a human's deck and
 * token hash, a bot's deck, name and generator state, an open seat's join code hash.
 */
public record SeatEntity(
        String kind,
        PlayerId player,
        String deck,
        String tokenHash,
        String bot,
        long rngState,
        String joinCodeHash) {
}
