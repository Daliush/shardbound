package fr.daliush.shardbound.core.event;

import fr.daliush.shardbound.core.state.PlayerId;

/** Who may see an event as it is (3.7). Others see its redacted form. */
public sealed interface Visibility {

    Visibility PUBLIC = new Public();

    record Public() implements Visibility {}

    record PrivateTo(PlayerId player) implements Visibility {}

    default boolean isVisibleTo(PlayerId viewer) {
        return switch (this) {
            case Public ignored -> true;
            case PrivateTo privateTo -> privateTo.player() == viewer;
        };
    }
}
