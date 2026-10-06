package fr.daliush.shardbound.core.rules.trigger;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.rules.effect.TargetOptions;
import fr.daliush.shardbound.core.rules.game.Game;
import java.util.ArrayList;
import java.util.List;

/**
 * A triggered ability picks all of its targets when it starts resolving, in printed order (10.6).
 * The controller is asked only when there are at least two options.
 */
public final class AbilityTargets {

    private AbilityTargets() {
    }

    public static void choose(Game game, Step.ChooseTargets step) {
        List<Effect> effects = step.source().effects().effects(game.catalog());
        List<List<TargetRef>> chosen = new ArrayList<>(step.chosen());
        while (chosen.size() < effects.size()) {
            List<TargetRef> options = TargetOptions.forEffect(game, step.source().controller(),
                    effects.get(chosen.size()));
            if (options.size() > 1) {
                game.pauseAndAsk(new Step.ChooseTargets(step.source(), chosen), step.source().controller(),
                        DecisionKind.CHOOSE_TARGET, options.stream().<Action>map(Action.ChooseTarget::new).toList());
                return;
            }
            chosen.add(options);
        }
        game.push(new Step.ResolveEffects(step.source(), 0, chosen));
    }

    /** The chosen target is recorded; the next pass picks the following one. */
    public static void resume(Game game, Step.ChooseTargets step, Action answer) {
        if (!(answer instanceof Action.ChooseTarget choice)) {
            throw new IllegalStateException(answer + " does not answer a target choice");
        }
        List<List<TargetRef>> chosen = new ArrayList<>(step.chosen());
        chosen.add(List.of(choice.target()));
        game.push(new Step.ChooseTargets(step.source(), chosen));
    }
}
