package fr.daliush.shardbound.core.state;

import fr.daliush.shardbound.core.content.CardId;

public record Relic(InstanceId id, CardId card, PlayerId owner, PlayerId controller, int arrivalSeq) {

    public CardInstance asCard() {
        return new CardInstance(id, card, owner);
    }
}
