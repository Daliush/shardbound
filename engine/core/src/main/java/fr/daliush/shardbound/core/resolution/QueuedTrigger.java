package fr.daliush.shardbound.core.resolution;

import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.PlayerId;

/**
 * An ability that triggered and waits for its turn to resolve. It keeps the source's controller and arrival
 * sequence, so a card that just left the board keeps its place in the order (9.8).
 */
public record QueuedTrigger(CardInstance source, PlayerId controller, Trigger trigger, int abilityIndex,
                            int arrivalSeq) {
}
