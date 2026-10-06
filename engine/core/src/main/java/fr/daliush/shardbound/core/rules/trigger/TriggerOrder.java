package fr.daliush.shardbound.core.rules.trigger;

import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.resolution.QueuedTrigger;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.Comparator;
import java.util.List;

/**
 * Orders abilities that triggered at the same time (9.8, 9.10): the active player's first, then the earliest
 * arrival on the board, then Echo, Death and Departure for one card, then printed order.
 */
public final class TriggerOrder {

    private TriggerOrder() {
    }

    public static List<QueuedTrigger> sorted(List<QueuedTrigger> raised, PlayerId active) {
        Comparator<QueuedTrigger> order = Comparator
                .comparingInt((QueuedTrigger trigger) -> trigger.controller() == active ? 0 : 1)
                .thenComparingInt(QueuedTrigger::arrivalSeq)
                .thenComparingInt(trigger -> rank(trigger.trigger()))
                .thenComparingInt(QueuedTrigger::abilityIndex);
        return raised.stream().sorted(order).toList();
    }

    private static int rank(Trigger trigger) {
        return switch (trigger) {
            case DEATH -> 1;
            case DEPARTURE -> 2;
            default -> 3;
        };
    }
}
