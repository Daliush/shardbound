package fr.daliush.shardbound.api.session;

import java.util.OptionalLong;

/** A game to create: the creator's deck, the opponent, and the seed if it is chosen. */
public record NewGame(String deck, Opponent opponent, OptionalLong seed) {

    public sealed interface Opponent {

        record Bot(String name, String deck) implements Opponent {}

        record Human() implements Opponent {}
    }
}
