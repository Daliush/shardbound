package fr.daliush.shardbound.core.rules.game;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.rules.combat.AttackSequence;
import fr.daliush.shardbound.core.rules.effect.EffectResolution;
import fr.daliush.shardbound.core.rules.play.CardPlay;
import fr.daliush.shardbound.core.rules.trigger.AbilityTargets;
import fr.daliush.shardbound.core.rules.turn.TurnEnd;
import fr.daliush.shardbound.core.rules.turn.TurnStart;

/** Sends each step to the rule that knows how to run it. */
public final class StepRunner {

    private StepRunner() {
    }

    public static void run(Game game, Step step) {
        switch (step) {
            case Step.StartTurn start -> TurnStart.run(game, start.player());
            case Step.TriggerTurnEnd end -> TurnEnd.triggerAbilities(game, end.player());
            case Step.FinishTurn finish -> TurnEnd.finish(game, finish.player());
            case Step.ResolvePlay play -> CardPlay.resolve(game, play);
            case Step.FinishSpell finish -> CardPlay.finishSpell(game, finish.spell());
            case Step.ResolveAttack attack -> AttackSequence.run(game, attack);
            case Step.ChooseTargets choose -> AbilityTargets.choose(game, choose);
            case Step.ResolveEffects effects -> EffectResolution.resolveNext(game, effects);
        }
    }

    /**
     * Gives a player's answer to the step that paused to ask for it ({@link Game#pauseAndAsk}).
     * Every step is listed, so a new step must say whether it can pause.
     */
    public static void resume(Game game, Step paused, Action answer) {
        switch (paused) {
            case Step.ResolveAttack attack -> AttackSequence.resume(game, attack, answer);
            case Step.ChooseTargets choose -> AbilityTargets.resume(game, choose, answer);
            case Step.ResolveEffects effects -> EffectResolution.resume(game, effects, answer);
            case Step.StartTurn ignored -> throw neverPauses(paused);
            case Step.TriggerTurnEnd ignored -> throw neverPauses(paused);
            case Step.FinishTurn ignored -> throw neverPauses(paused);
            case Step.ResolvePlay ignored -> throw neverPauses(paused);
            case Step.FinishSpell ignored -> throw neverPauses(paused);
        }
    }

    private static IllegalStateException neverPauses(Step step) {
        return new IllegalStateException(step + " never pauses for a decision");
    }
}
