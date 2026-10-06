package fr.daliush.shardbound.core.view;

import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.state.PlayerId;

/** What the opponent is deciding, without their options. */
public record WaitingFor(PlayerId player, DecisionKind kind) {
}
