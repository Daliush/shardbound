package fr.daliush.shardbound.core.content;

import java.util.List;

/** A triggered or continuous ability: trigger, then effects (6.4). */
public record Ability(Trigger trigger, List<Effect> effects) {

    public Ability {
        effects = List.copyOf(effects);
    }
}
