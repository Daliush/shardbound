package fr.daliush.shardbound.core.content;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** One of a unit's attack abilities (7.1), possibly with Echo X (11.1). */
public record AttackAbility(Optional<String> name, int cost, List<Effect> effects, OptionalInt echo) {

    public AttackAbility {
        effects = List.copyOf(effects);
    }

    /** An attack without an effect on its target cannot be intercepted (7.8). */
    public boolean hasTarget() {
        return effects.stream().anyMatch(effect -> effect.targets(TargetSpec.ATTACK_TARGET));
    }
}
