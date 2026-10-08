package fr.daliush.shardbound.core.determinization;

import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.view.PlayerView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The opponent's hand cards the viewer knows: those the history saw go back to their hand in public, until they leave
 * it again. A Fracture card between two steps keeps its next step and the turn its last one was played (11.2.2).
 */
final class KnownHand {

    private KnownHand() {
    }

    static List<HandCard> of(PlayerView view) {
        PlayerId opponent = view.viewer().opponent();
        Map<InstanceId, HandCard> known = new LinkedHashMap<>();
        int turn = 0;
        for (GameEvent event : view.history()) {
            switch (event) {
                case GameEvent.TurnStarted started -> turn = started.turn();
                case GameEvent.FractureAdvanced advanced when advanced.card().owner() == opponent ->
                        known.put(advanced.card().id(), new HandCard(advanced.card(), advanced.nextStep() - 1, turn));
                case GameEvent.ReturnedToHand returned when returned.card().owner() == opponent ->
                        known.put(returned.card().id(), HandCard.fresh(returned.card()));
                case GameEvent.Recalled recalled when recalled.card().owner() == opponent ->
                        known.put(recalled.card().id(), HandCard.fresh(recalled.card()));
                case GameEvent.CardPlayed played -> known.remove(played.card().id());
                case GameEvent.CardDiscarded discarded -> known.remove(discarded.card().id());
                case GameEvent.MulliganTaken mulligan when mulligan.player() == opponent -> known.clear();
                default -> {
                    // Nothing else moves a card the viewer knows into or out of the opponent's hand.
                }
            }
        }
        return List.copyOf(known.values());
    }
}
