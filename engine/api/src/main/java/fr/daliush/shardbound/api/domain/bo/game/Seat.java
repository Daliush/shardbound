package fr.daliush.shardbound.api.domain.bo.game;

import fr.daliush.shardbound.core.content.DeckId;
import fr.daliush.shardbound.core.state.PlayerId;

/** Who sits at a seat. Secrets are kept as SHA-256 hashes, never as they are. */
public sealed interface Seat {

    PlayerId player();

    /** A person, who proves it is them with their seat token. */
    record Human(PlayerId player, DeckId deck, String tokenHash) implements Seat {}

    /** A program, rebuilt at every move from its name and the state of its generator. */
    record Bot(PlayerId player, DeckId deck, String name, long rngState) implements Seat {

        public Bot withRngState(long state) {
            return new Bot(player, deck, name, state);
        }
    }

    /** A seat waiting for the person who holds the join code. */
    record Open(PlayerId player, String joinCodeHash) implements Seat {}
}
