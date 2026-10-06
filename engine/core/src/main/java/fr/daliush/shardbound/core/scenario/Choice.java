package fr.daliush.shardbound.core.scenario;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.state.GameState;
import java.util.List;
import java.util.function.Predicate;

/** Picks one action of a decision, so scenarios never depend on action indexes. */
@FunctionalInterface
public interface Choice {

    Action pick(GameState state, Decision decision);

    /** The only action of the decision that matches; anything else is a mistake in the scenario. */
    static Action single(Decision decision, Predicate<Action> matches, String description) {
        List<Action> found = decision.actions().stream().filter(matches).toList();
        if (found.size() != 1) {
            throw new IllegalStateException(found.size() + " actions match \"" + description + "\" in "
                    + decision.kind() + " decision " + decision.id() + ": " + decision.actions());
        }
        return found.getFirst();
    }
}
