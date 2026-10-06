package fr.daliush.shardbound.core.resolution;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.Optional;

/**
 * Everything an effect needs to know about where it comes from: the effects, the card instance
 * (for {@code self}), its controller (for {@code you}) and, inside an attack, the attack's target.
 */
public record EffectSource(EffectList effects, InstanceId instance, PlayerId controller,
                           Optional<TargetRef> attackTarget) {

    public boolean isAttack() {
        return effects instanceof EffectList.AttackEffects;
    }
}
