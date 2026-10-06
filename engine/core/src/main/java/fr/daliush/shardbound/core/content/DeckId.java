package fr.daliush.shardbound.core.content;

import java.util.Objects;

/** A deck's stable identifier, such as {@code ember-starter}. */
public record DeckId(String value) {

    public DeckId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value;
    }
}
