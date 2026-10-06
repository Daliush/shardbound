package fr.daliush.shardbound.core.rules.combat;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.Ability;
import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.event.EventTarget;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.EffectList;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.rules.trigger.Abilities;
import fr.daliush.shardbound.core.state.Unit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 7.4, one phase per step: declare and pay, resolve "Attack" abilities (9.9), offer an intercept (7.5),
 * then apply the attack ability's effects to the (possibly redirected) target.
 */
public final class AttackSequence {

    private AttackSequence() {
    }

    public static void run(Game game, Step.ResolveAttack step) {
        switch (step.phase()) {
            case DECLARE -> declare(game, step);
            case INTERCEPT -> offerIntercept(game, step);
            case EFFECTS -> applyEffects(game, step);
        }
    }

    private static void declare(Game game, Step.ResolveAttack step) {
        Unit attacker = game.unit(step.attacker().id()).orElseThrow();
        UnitCard card = game.catalog().unit(attacker.card());
        AttackAbility attack = card.attacks().get(step.attackIndex());
        game.updatePlayer(step.player(), state -> state.withShards(state.shards().pay(attack.cost())));
        game.updateUnit(attacker.markHasAttacked());
        game.emit(new GameEvent.AttackDeclared(attacker.asCard(), step.attackIndex(),
                step.target().map(target -> describe(game, target))));

        List<Step> next = new ArrayList<>();
        for (int index = 0; index < card.abilities().size(); index++) {
            Ability ability = card.abilities().get(index);
            if (ability.trigger() == Trigger.ATTACK) {
                next.add(Abilities.begin(game, attacker.asCard(), step.player(), Trigger.ATTACK, index));
            }
        }
        next.add(step.inPhase(Step.AttackPhase.INTERCEPT));
        game.push(next.toArray(Step[]::new));
    }

    /** 7.5: the defender may redirect the attack, if one of their other units can intercept. */
    private static void offerIntercept(Game game, Step.ResolveAttack step) {
        if (attackerLeft(game, step)) {
            return;
        }
        List<Unit> interceptors = eligibleInterceptors(game, step);
        if (interceptors.isEmpty()) {
            game.push(step.inPhase(Step.AttackPhase.EFFECTS));
            return;
        }
        List<Action> answers = interceptAnswers(interceptors);
        game.pauseAndAsk(step, step.player().opponent(), DecisionKind.INTERCEPT, answers);
    }

    /** Nobody intercepts an attack on a player, or on a unit that already left the board (7.9). */
    private static List<Unit> eligibleInterceptors(Game game, Step.ResolveAttack step) {
        Optional<Unit> target = step.target().flatMap(ref -> unitOf(game, ref));
        if (target.isEmpty()) {
            return List.of();
        }
        return Interceptors.eligible(game, step.player().opponent(), target.get());
    }

    /** Declining first, then one answer per interceptor, by arrival (spec §6.2). */
    private static List<Action> interceptAnswers(List<Unit> interceptors) {
        List<Action> answers = new ArrayList<>();
        answers.add(new Action.DeclineIntercept());
        for (Unit interceptor : interceptors) {
            answers.add(new Action.Intercept(interceptor.id()));
        }
        return answers;
    }

    /** The defender's answer to the intercept question: the only question an attack asks. */
    public static void resume(Game game, Step.ResolveAttack step, Action answer) {
        if (step.phase() != Step.AttackPhase.INTERCEPT) {
            throw new IllegalStateException("An attack only waits for a decision in its INTERCEPT phase: " + step);
        }
        switch (answer) {
            case Action.Intercept intercept -> redirect(game, step, intercept);
            case Action.DeclineIntercept ignored -> decline(game, step);
            default -> throw new IllegalStateException(answer + " does not answer an intercept");
        }
    }

    private static void redirect(Game game, Step.ResolveAttack step, Action.Intercept intercept) {
        Unit interceptor = game.unit(intercept.interceptor()).orElseThrow();
        Unit original = step.target().flatMap(target -> unitOf(game, target)).orElseThrow();
        game.updateUnit(interceptor.markHasIntercepted());
        game.emit(new GameEvent.AttackIntercepted(original.asCard(), interceptor.asCard()));
        game.push(step.redirectedTo(TargetRef.unit(interceptor.id())).inPhase(Step.AttackPhase.EFFECTS));
    }

    private static void decline(Game game, Step.ResolveAttack step) {
        game.emit(new GameEvent.InterceptDeclined(step.player().opponent()));
        game.push(step.inPhase(Step.AttackPhase.EFFECTS));
    }

    private static void applyEffects(Game game, Step.ResolveAttack step) {
        if (attackerLeft(game, step)) {
            return;
        }
        Unit attacker = game.unit(step.attacker().id()).orElseThrow();
        EffectList effects = new EffectList.AttackEffects(attacker.card(), step.attackIndex());
        int effectCount = effects.effects(game.catalog()).size();
        EffectSource source = new EffectSource(effects, attacker.id(), step.player(), step.target());
        game.push(new Step.ResolveEffects(source, 0, Collections.nCopies(effectCount, List.of())));
    }

    /** 7.10: an attacker that left the board before its attack applies does not attack. */
    private static boolean attackerLeft(Game game, Step.ResolveAttack step) {
        if (game.unit(step.attacker().id()).isPresent()) {
            return false;
        }
        game.emit(new GameEvent.AttackCancelled(step.attacker()));
        return true;
    }

    private static Optional<Unit> unitOf(Game game, TargetRef target) {
        return target instanceof TargetRef.UnitTarget unit ? game.unit(unit.id()) : Optional.empty();
    }

    private static EventTarget describe(Game game, TargetRef target) {
        return switch (target) {
            case TargetRef.UnitTarget unit -> new EventTarget.UnitHit(game.unit(unit.id()).orElseThrow().asCard());
            case TargetRef.PlayerTarget player -> new EventTarget.PlayerHit(player.player());
            default -> throw new IllegalStateException("An attack cannot target " + target);
        };
    }
}
