package fr.daliush.shardbound.core.resolution;

import java.util.List;

/**
 * Pending work. Steps run front first; queued triggers start only once there is no step left,
 * so an action resolves completely before the abilities it triggered (9.11).
 */
public record Resolution(List<Step> steps, List<QueuedTrigger> queue) {

    public Resolution {
        steps = List.copyOf(steps);
        queue = List.copyOf(queue);
    }

    public static Resolution idle() {
        return new Resolution(List.of(), List.of());
    }

    public boolean isIdle() {
        return steps.isEmpty() && queue.isEmpty();
    }
}
