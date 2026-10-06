package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.rules.board.Departures;
import fr.daliush.shardbound.core.rules.game.Game;
import java.util.List;

/** 8.2 Destroy: a unit or a relic goes to the graveyard, whatever its defense. */
final class DestroyEffect {

    private DestroyEffect() {
    }

    static void apply(Game game, Effect.Destroy destroy, EffectSource source, List<TargetRef> chosen) {
        for (TargetRef target : Targets.resolve(game, destroy.target(), source, chosen)) {
            switch (target) {
                case TargetRef.UnitTarget unit ->
                        game.unit(unit.id()).ifPresent(found -> Departures.destroy(game, found, List.of("8.2")));
                case TargetRef.RelicTarget relic ->
                        game.relic(relic.id()).ifPresent(found -> Departures.destroy(game, found));
                default -> throw new IllegalStateException("Destroy cannot hit " + target);
            }
        }
    }
}
