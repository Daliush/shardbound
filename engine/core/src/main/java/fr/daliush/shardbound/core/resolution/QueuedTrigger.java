package fr.daliush.shardbound.core.resolution;

import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;

/**
 * Something that triggered and waits for its turn to resolve: an ability (section 9) or the echoes of a unit that died
 * (11.1). It keeps the source's controller and arrival sequence, so a card that just left the board keeps its place in
 * the order (9.8).
 */
public sealed interface QueuedTrigger {

    CardInstance source();

    PlayerId controller();

    int arrivalSeq();

    /** One of the card's abilities, by its index among them. */
    record TriggeredAbility(CardInstance source, PlayerId controller, Trigger trigger, int abilityIndex,
                            int arrivalSeq) implements QueuedTrigger {}

    /** 11.1.1: the unit's attack abilities with Echo, by index; with two, its owner orders them (11.1.8). */
    record Echoes(CardInstance source, PlayerId controller, List<Integer> attackIndexes, int arrivalSeq)
            implements QueuedTrigger {

        public Echoes {
            attackIndexes = List.copyOf(attackIndexes);
        }
    }
}
