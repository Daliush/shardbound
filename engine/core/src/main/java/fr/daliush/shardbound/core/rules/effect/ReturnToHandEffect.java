package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.rules.board.Departures;
import fr.daliush.shardbound.core.rules.game.Game;
import java.util.List;

/** 8.8 Return to hand: a unit or a relic leaves the board for its owner's hand, without dying. */
final class ReturnToHandEffect {

    private ReturnToHandEffect() {
    }

    static void apply(Game game, Effect.ReturnToHand returnToHand, EffectSource source, List<TargetRef> chosen) {
        for (TargetRef target : Targets.resolve(game, returnToHand.target(), source, chosen)) {
            switch (target) {
                case TargetRef.UnitTarget unit ->
                        game.unit(unit.id()).ifPresent(found -> Departures.returnToHand(game, found));
                case TargetRef.RelicTarget relic ->
                        game.relic(relic.id()).ifPresent(found -> Departures.returnToHand(game, found));
                default -> throw new IllegalStateException("Return to hand cannot take " + target);
            }
        }
    }
}
