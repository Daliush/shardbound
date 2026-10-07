package fr.daliush.shardbound.core.rules.combat;

import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.TargetSpec;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;
import java.util.OptionalInt;

/** What an attack deals to its target, the attacker's bonuses included (8.5, 8.18), for views and prompts. */
public final class AttackDamage {

    private AttackDamage() {
    }

    /** Empty when the attack deals no damage to its target. */
    public static OptionalInt toTarget(AttackAbility attack, Unit attacker) {
        List<Effect.Damage> hits = attack.effects().stream()
                .filter(effect -> effect.targets(TargetSpec.ATTACK_TARGET))
                .filter(Effect.Damage.class::isInstance)
                .map(Effect.Damage.class::cast)
                .toList();
        if (hits.isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(hits.stream().mapToInt(hit -> hit.withBonus(attacker.attackBonus())).sum());
    }
}
