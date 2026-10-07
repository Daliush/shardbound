package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;

/** 8.13 Recall: a unit card goes from its controller's graveyard back to their hand, without its modifications. */
final class RecallEffect {

    private RecallEffect() {
    }

    static void apply(Game game, Effect.Recall recall, EffectSource source, List<TargetRef> chosen) {
        PlayerId controller = source.controller();
        List<TargetRef> stillValid = TargetOptions.forEffect(game, controller, recall);
        for (TargetRef target : chosen.stream().filter(stillValid::contains).toList()) {
            TargetRef.GraveyardCardTarget inGraveyard = (TargetRef.GraveyardCardTarget) target;
            CardInstance card = game.player(controller).graveyardCard(inGraveyard.id()).orElseThrow();
            if (game.player(controller).handIsFull()) {
                game.emit(new GameEvent.RecallFailed(card));
            } else {
                game.updatePlayer(controller,
                        state -> state.removeFromGraveyard(card.id()).addToHand(HandCard.fresh(card)));
                game.emit(new GameEvent.Recalled(card));
            }
        }
    }
}
