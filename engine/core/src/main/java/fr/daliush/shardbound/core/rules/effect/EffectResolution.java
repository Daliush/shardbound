package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.rules.game.Game;
import java.util.List;

/** Applies a list of effects in printed order, one atomic effect per step (spec §7). */
public final class EffectResolution {

    private EffectResolution() {
    }

    public static void resolveNext(Game game, Step.ResolveEffects step) {
        List<Effect> effects = step.source().effects().effects(game.catalog());
        if (step.nextEffect() + 1 < effects.size()) {
            game.push(step.advanced());
        }
        apply(game, effects.get(step.nextEffect()), step.source(), step.chosen().get(step.nextEffect()));
    }

    private static void apply(Game game, Effect effect, EffectSource source, List<TargetRef> chosen) {
        switch (effect) {
            case Effect.Damage damage -> DamageEffect.apply(game, damage, source, chosen);
            case Effect.Destroy destroy -> DestroyEffect.apply(game, destroy, source, chosen);
            case Effect.Heal heal -> HealEffect.apply(game, heal, source, chosen);
            case Effect.Draw draw -> DrawEffect.apply(game, draw, source, chosen);
            case Effect.Summon summon -> SummonEffect.apply(game, summon, source);
            default -> throw new IllegalStateException("Not implemented yet: " + effect);
        }
    }
}
