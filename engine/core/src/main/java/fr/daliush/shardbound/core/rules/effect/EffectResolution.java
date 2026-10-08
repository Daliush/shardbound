package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.DiscardChoice;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;

/**
 * Applies a list of effects in printed order, one atomic effect per step (spec §7). An effect that makes a player
 * pick cards pauses its step until they answer.
 */
public final class EffectResolution {

    private EffectResolution() {
    }

    public static void resolveNext(Game game, Step.ResolveEffects step) {
        switch (effectAt(game, step)) {
            case Effect.Sacrifice sacrifice -> sacrifice(game, step, sacrifice);
            case Effect.Discard discard when discard.choice() == DiscardChoice.PLAYER -> discard(game, step, discard);
            case Effect effect -> {
                continueAfter(game, step);
                apply(game, effect, step.source(), step.chosen().get(step.nextEffect()));
            }
        }
    }

    /** The answer to a choice of cards: the paused effect applies to them, then the next effect follows. */
    public static void resume(Game game, Step.ResolveEffects step, Action answer) {
        if (!(answer instanceof Action.ChooseCards choice)) {
            throw new IllegalStateException(answer + " does not answer a choice of cards");
        }
        continueAfter(game, step);
        applyToCards(game, effectAt(game, step), choice.cards());
    }

    /** 8.22: a sacrifice that can no longer be made stops the list here. */
    private static void sacrifice(Game game, Step.ResolveEffects step, Effect.Sacrifice sacrifice) {
        PlayerId controller = step.source().controller();
        List<List<InstanceId>> options = SacrificeEffect.options(game, sacrifice, controller);
        if (options.isEmpty()) {
            SacrificeEffect.fail(game, sacrifice, controller);
            return;
        }
        pickCards(game, step, controller, options);
    }

    /** 8.7: the discarding player picks the cards, even during the other player's turn (5.5.2). */
    private static void discard(Game game, Step.ResolveEffects step, Effect.Discard discard) {
        List<PlayerId> discarders = DiscardEffect.discarders(game, discard, step.source(),
                step.chosen().get(step.nextEffect()));
        if (discarders.isEmpty()) {
            continueAfter(game, step);
            return;
        }
        PlayerId discarder = discarders.getFirst();
        pickCards(game, step, discarder, DiscardEffect.options(game.player(discarder), discard.amount()));
    }

    /** The only option is taken at once; with two or more, the player chooses (CHOOSE_CARDS). */
    private static void pickCards(Game game, Step.ResolveEffects step, PlayerId player,
                                  List<List<InstanceId>> options) {
        if (options.size() == 1) {
            continueAfter(game, step);
            applyToCards(game, effectAt(game, step), options.getFirst());
            return;
        }
        game.pauseAndAsk(step, player, DecisionKind.CHOOSE_CARDS,
                options.stream().<Action>map(Action.ChooseCards::new).toList());
    }

    private static void applyToCards(Game game, Effect effect, List<InstanceId> cards) {
        switch (effect) {
            case Effect.Sacrifice ignored -> SacrificeEffect.apply(game, cards);
            case Effect.Discard ignored -> DiscardEffect.apply(game, cards);
            default -> throw new IllegalStateException(effect + " never asks for cards");
        }
    }

    private static void apply(Game game, Effect effect, EffectSource source, List<TargetRef> chosen) {
        switch (effect) {
            case Effect.Damage damage -> DamageEffect.apply(game, damage, source, chosen);
            case Effect.Destroy destroy -> DestroyEffect.apply(game, destroy, source, chosen);
            case Effect.Heal heal -> HealEffect.apply(game, heal, source, chosen);
            case Effect.Modify modify -> ModifyEffect.apply(game, modify, source, chosen);
            case Effect.Draw draw -> DrawEffect.apply(game, draw, source, chosen);
            case Effect.Discard discard -> DiscardEffect.atRandom(game, discard, source, chosen);
            case Effect.ReturnToHand returnToHand -> ReturnToHandEffect.apply(game, returnToHand, source, chosen);
            case Effect.Summon summon -> SummonEffect.apply(game, summon, source);
            case Effect.Freeze freeze -> FreezeEffect.apply(game, freeze, source, chosen);
            case Effect.GainShards gain -> GainShardsEffect.apply(game, gain, source);
            case Effect.Recall recall -> RecallEffect.apply(game, recall, source, chosen);
            case Effect.Link ignored -> LinkEffect.apply(game, chosen);
            case Effect.Sacrifice ignored -> throw asksForCards(effect);
            case Effect.StatAura ignored -> throw continuous(effect);
            case Effect.CostAura ignored -> throw continuous(effect);
        }
    }

    private static IllegalStateException asksForCards(Effect effect) {
        return new IllegalStateException(effect + " asks for cards: it resolves through pickCards");
    }

    /** 9.7: an aura applies while its card is on the board (rules.aura); it never resolves. */
    private static IllegalStateException continuous(Effect aura) {
        return new IllegalStateException(aura + " is continuous: it never resolves");
    }

    /** The next effect waits at the front, so it runs after this one and its state check. */
    private static void continueAfter(Game game, Step.ResolveEffects step) {
        if (step.nextEffect() + 1 < effects(game, step).size()) {
            game.push(step.advanced());
        }
    }

    private static Effect effectAt(Game game, Step.ResolveEffects step) {
        return effects(game, step).get(step.nextEffect());
    }

    private static List<Effect> effects(Game game, Step.ResolveEffects step) {
        return step.source().effects().effects(game.catalog());
    }
}
