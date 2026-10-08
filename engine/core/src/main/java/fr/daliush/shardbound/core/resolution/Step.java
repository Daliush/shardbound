package fr.daliush.shardbound.core.resolution;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;
import java.util.Optional;

/** One piece of work left in the current resolution. Steps are data, so a game can pause on a decision. */
public sealed interface Step {

    /** Whether the triggered abilities waiting in the queue must resolve before this step runs. */
    default boolean waitsForTriggers() {
        return false;
    }

    /** 5.2: start of a player's turn. */
    record StartTurn(PlayerId player) implements Step {}

    /** 5.4.1: the active player's "Turn end" abilities trigger. */
    record TriggerTurnEnd(PlayerId player) implements Step {}

    /** 5.4.2 and 5.4.3, once the "Turn end" abilities have resolved. */
    record FinishTurn(PlayerId player) implements Step {

        @Override
        public boolean waitsForTriggers() {
            return true;
        }
    }

    /** 5.4.4, then the next turn or a draw (1.5), once the abilities triggered by 5.4.3 have resolved. */
    record PassTurn(PlayerId player) implements Step {

        @Override
        public boolean waitsForTriggers() {
            return true;
        }
    }

    /** 6.3: playing a card. */
    record ResolvePlay(PlayerId player, Action.PlayCard play) implements Step {}

    /** 6.3: a resolved spell goes to its owner's graveyard, or back to their hand between Fracture steps (11.2.2). */
    record FinishSpell(HandCard spell) implements Step {}

    /** 7.4: an attack, one phase at a time. */
    record ResolveAttack(PlayerId player, CardInstance attacker, int attackIndex, Optional<TargetRef> target,
                         AttackPhase phase) implements Step {

        public ResolveAttack inPhase(AttackPhase next) {
            return new ResolveAttack(player, attacker, attackIndex, target, next);
        }

        public ResolveAttack redirectedTo(TargetRef newTarget) {
            return new ResolveAttack(player, attacker, attackIndex, Optional.of(newTarget), phase);
        }
    }

    /** 11.1.1: the echoes of a unit that died; with two, its owner chooses their order first (11.1.8). */
    record StartEchoes(CardInstance unit, PlayerId controller, List<Integer> attacks) implements Step {

        public StartEchoes {
            attacks = List.copyOf(attacks);
        }
    }

    /** 10.6: a triggered ability or an echo picks all of its targets before its first effect. */
    record ChooseTargets(EffectSource source, List<List<TargetRef>> chosen) implements Step {

        public ChooseTargets {
            chosen = List.copyOf(chosen);
        }
    }

    /** Resolves the effects of a spell, an attack or an ability, one atomic effect per step. */
    record ResolveEffects(EffectSource source, int nextEffect, List<List<TargetRef>> chosen) implements Step {

        public ResolveEffects {
            chosen = List.copyOf(chosen);
        }

        public ResolveEffects advanced() {
            return new ResolveEffects(source, nextEffect + 1, chosen);
        }
    }

    enum AttackPhase {
        DECLARE, INTERCEPT, EFFECTS
    }
}
