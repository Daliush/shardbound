package fr.daliush.shardbound.core.state;

import fr.daliush.shardbound.core.content.CardId;

/** A card in a zone: which copy, which card, whose deck it came from. */
public record CardInstance(InstanceId id, CardId card, PlayerId owner) {
}
