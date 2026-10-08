package fr.daliush.shardbound.core.rules.trigger;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.TargetSpec;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.rules.combat.AttackOptions;
import fr.daliush.shardbound.core.rules.effect.Sacrifices;
import fr.daliush.shardbound.core.rules.effect.TargetOptions;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.PlayerState;
import java.util.ArrayList;
import java.util.List;

/**
 * A triggered ability or an echo picks all of its targets when it starts resolving, in printed order (10.6), unless it
 * cannot make its sacrifices (8.22); an echo first picks its new attack target (11.1.3). The controller is asked only
 * when there are at least two options.
 */
public final class AbilityTargets {

    private AbilityTargets() {
    }

    public static void choose(Game game, Step.ChooseTargets step) {
        List<Effect> effects = step.source().effects().effects(game.catalog());
        PlayerState controller = game.player(step.source().controller());
        int sacrifices = Sacrifices.askedBy(effects);
        if (step.chosen().isEmpty() && !Sacrifices.canMake(controller, game.catalog(), sacrifices)) {
            // 8.22: the ability starts resolving, and without all of its sacrifices it does nothing at all.
            game.emit(new GameEvent.SacrificeFailed(controller.id(), sacrifices,
                    Sacrifices.available(controller, game.catalog())));
            return;
        }
        EffectSource source = step.source();
        if (needsAttackTarget(game, source)) {
            List<TargetRef> options = AttackOptions.targets(game, source.controller());
            if (options.size() > 1) {
                ask(game, step, options);
                return;
            }
            source = source.withAttackTarget(options.getFirst());
        }
        List<List<TargetRef>> chosen = new ArrayList<>(step.chosen());
        while (chosen.size() < effects.size()) {
            List<TargetRef> options = TargetOptions.forEffect(game, source.controller(), effects.get(chosen.size()));
            if (options.size() > 1) {
                ask(game, new Step.ChooseTargets(source, chosen), options);
                return;
            }
            chosen.add(options);
        }
        game.push(new Step.ResolveEffects(source, 0, chosen));
    }

    /** The chosen target is recorded; the next pass picks the following one. */
    public static void resume(Game game, Step.ChooseTargets step, Action answer) {
        if (!(answer instanceof Action.ChooseTarget choice)) {
            throw new IllegalStateException(answer + " does not answer a target choice");
        }
        if (needsAttackTarget(game, step.source())) {
            game.push(new Step.ChooseTargets(step.source().withAttackTarget(choice.target()), step.chosen()));
            return;
        }
        List<List<TargetRef>> chosen = new ArrayList<>(step.chosen());
        chosen.add(List.of(choice.target()));
        game.push(new Step.ChooseTargets(step.source(), chosen));
    }

    /** 11.1.3: an echo with a target gets a new one, chosen with the targeting rules of an attack (7.3). */
    private static boolean needsAttackTarget(Game game, EffectSource source) {
        return source.attackTarget().isEmpty() && source.effects().effects(game.catalog()).stream()
                .anyMatch(effect -> effect.targets(TargetSpec.ATTACK_TARGET));
    }

    private static void ask(Game game, Step.ChooseTargets step, List<TargetRef> options) {
        game.pauseAndAsk(step, step.source().controller(), DecisionKind.CHOOSE_TARGET,
                options.stream().<Action>map(Action.ChooseTarget::new).toList());
    }
}
