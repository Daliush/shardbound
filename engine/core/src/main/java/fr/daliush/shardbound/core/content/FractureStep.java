package fr.daliush.shardbound.core.content;

import java.util.List;

/** One step of a Fracture spell (11.2). */
public record FractureStep(int cost, List<Effect> effects) {

    public FractureStep {
        effects = List.copyOf(effects);
    }
}
