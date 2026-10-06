package fr.daliush.shardbound.core.state;

import fr.daliush.shardbound.core.content.Duration;

/** A Modify effect on a unit (8.5), kept so it can expire. */
public record Modifier(int attackDamage, int defense, Duration duration, InstanceId source) {
}
