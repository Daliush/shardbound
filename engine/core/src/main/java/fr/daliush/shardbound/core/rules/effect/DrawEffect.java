package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.rules.turn.CardDraws;
import java.util.List;

/** 8.6 Draw X: one card at a time, so a full hand (3.3) and fatigue (1.4) apply to each. */
final class DrawEffect {

    private DrawEffect() {
    }

    static void apply(Game game, Effect.Draw draw, EffectSource source, List<TargetRef> chosen) {
        for (TargetRef target : Targets.resolve(game, draw.target(), source, chosen)) {
            TargetRef.PlayerTarget player = (TargetRef.PlayerTarget) target;
            for (int i = 0; i < draw.amount(); i++) {
                CardDraws.draw(game, player.player(), "8.6");
            }
        }
    }
}
