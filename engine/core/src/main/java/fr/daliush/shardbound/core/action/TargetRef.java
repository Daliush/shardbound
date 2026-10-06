package fr.daliush.shardbound.core.action;

import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;

/** A concrete target, chosen by a player or by the engine. */
public sealed interface TargetRef {

    record UnitTarget(InstanceId id) implements TargetRef {}

    record RelicTarget(InstanceId id) implements TargetRef {}

    record PlayerTarget(PlayerId player) implements TargetRef {}

    record GraveyardCardTarget(InstanceId id) implements TargetRef {}

    static TargetRef unit(InstanceId id) {
        return new UnitTarget(id);
    }

    static TargetRef player(PlayerId player) {
        return new PlayerTarget(player);
    }
}
