package fr.daliush.shardbound.api.domain.bo.game;

import fr.daliush.shardbound.core.view.PlayerView;
import java.util.List;
import java.util.Optional;

/**
 * What one seat may see of a state: the engine's view, what each card of its hand costs now, and, when the seat must
 * decide, the engine's prompt and button labels. They are written while the full state is at hand, since cost auras
 * (8.14) and the describers read it. {@code handCosts} follow the order of the hand.
 */
public record SeatSnapshot(PlayerView view, List<Integer> handCosts, Optional<DecisionText> decisionText) {

    public SeatSnapshot {
        handCosts = List.copyOf(handCosts);
    }

    /** {@code labels} follow the order of the decision's actions. */
    public record DecisionText(String prompt, List<String> labels) {

        public DecisionText {
            labels = List.copyOf(labels);
        }
    }
}
