package fr.daliush.shardbound.core.bot;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.view.PlayerView;

/** Anything that plays: it sees only its view and picks one of the decision's actions (design doc §3.4). */
public interface Player {

    Action choose(PlayerView view, Decision decision);
}
