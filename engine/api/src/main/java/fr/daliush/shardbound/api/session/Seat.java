package fr.daliush.shardbound.api.session;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fr.daliush.shardbound.core.content.DeckId;
import fr.daliush.shardbound.core.state.PlayerId;

/** Who sits at a seat. Secrets are stored as SHA-256 hashes, never as they are. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Seat.Human.class, name = "human"),
    @JsonSubTypes.Type(value = Seat.Bot.class, name = "bot"),
    @JsonSubTypes.Type(value = Seat.Open.class, name = "open")
})
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
