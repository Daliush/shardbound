package fr.daliush.shardbound.core.decision;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;

/** What the game waits for: who decides, and the legal actions in canonical order (spec §6.2). */
public record Decision(String id, PlayerId player, DecisionKind kind, List<Action> actions) {

    public Decision {
        actions = List.copyOf(actions);
    }
}
