package fr.daliush.shardbound.api.domain.bo.game;

import fr.daliush.shardbound.core.view.PlayerView;
import java.util.List;
import java.util.Optional;

/**
 * What one seat may see of a state: the engine's view, and, when the seat must decide, the engine's prompt
 * and button labels. They are written while the full state is at hand, since the describers read it.
 */
public record SeatSnapshot(PlayerView view, Optional<DecisionText> decisionText) {

    /** {@code labels} follow the order of the decision's actions. */
    public record DecisionText(String prompt, List<String> labels) {

        public DecisionText {
            labels = List.copyOf(labels);
        }
    }
}
