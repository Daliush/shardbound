package fr.daliush.shardbound.core.content;

import java.util.Objects;

/** A card's stable identifier, such as {@code ember.ash-warden}. */
public record CardId(String value) {

    public CardId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value;
    }
}
