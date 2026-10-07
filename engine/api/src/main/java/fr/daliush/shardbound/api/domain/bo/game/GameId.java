package fr.daliush.shardbound.api.domain.bo.game;

import java.util.Optional;
import java.util.UUID;

public record GameId(UUID value) {

    public static GameId random() {
        return new GameId(UUID.randomUUID());
    }

    /** Empty when the text is not a game id. */
    public static Optional<GameId> parse(String text) {
        try {
            return Optional.of(new GameId(UUID.fromString(text)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
