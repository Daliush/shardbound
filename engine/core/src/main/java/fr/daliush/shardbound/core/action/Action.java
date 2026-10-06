package fr.daliush.shardbound.core.action;

import fr.daliush.shardbound.core.state.InstanceId;
import java.util.List;
import java.util.Optional;

/**
 * Everything a player can answer to a decision. Actions are fully specified and listed by the engine:
 * a player picks one, and value equality is how the engine checks it.
 */
public sealed interface Action {

    record KeepHand() implements Action {}

    record Mulligan() implements Action {}

    /** {@code targets}: one entry per choice slot of the card, in printed order (spec §6.1). */
    record PlayCard(InstanceId card, boolean overcharge, List<TargetRef> targets, List<InstanceId> sacrificed)
            implements Action {

        public PlayCard {
            targets = List.copyOf(targets);
            sacrificed = List.copyOf(sacrificed);
        }
    }

    record Attack(InstanceId attacker, int attackIndex, Optional<TargetRef> target) implements Action {}

    record Intercept(InstanceId interceptor) implements Action {}

    record DeclineIntercept() implements Action {}

    record ChooseTarget(TargetRef target) implements Action {}

    record ChooseCards(List<InstanceId> cards) implements Action {

        public ChooseCards {
            cards = List.copyOf(cards);
        }
    }

    record ChooseOrder(List<Integer> order) implements Action {

        public ChooseOrder {
            order = List.copyOf(order);
        }
    }

    record EndTurn() implements Action {}
}
