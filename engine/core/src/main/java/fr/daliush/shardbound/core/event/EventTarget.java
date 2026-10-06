package fr.daliush.shardbound.core.event;

import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.PlayerId;

/** A target as events describe it: with the card, so the event can be read without the state. */
public sealed interface EventTarget {

    record UnitHit(CardInstance unit) implements EventTarget {}

    record PlayerHit(PlayerId player) implements EventTarget {}
}
