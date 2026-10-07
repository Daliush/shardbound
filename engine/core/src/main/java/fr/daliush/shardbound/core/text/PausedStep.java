package fr.daliush.shardbound.core.text;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.state.GameState;

/** A decision asked in the middle of a step keeps that step at the front of the pending work: what it waits on. */
final class PausedStep {

    private PausedStep() {
    }

    static <S extends Step> S of(GameState state, Class<S> type) {
        Step front = state.resolution().steps().getFirst();
        if (!type.isInstance(front)) {
            throw new IllegalStateException("Expected a paused " + type.getSimpleName() + ", found " + front);
        }
        return type.cast(front);
    }

    /** The effect that paused its list to ask for cards. */
    static Effect effect(GameState state, CardCatalog catalog) {
        Step.ResolveEffects step = of(state, Step.ResolveEffects.class);
        return step.source().effects().effects(catalog).get(step.nextEffect());
    }
}
