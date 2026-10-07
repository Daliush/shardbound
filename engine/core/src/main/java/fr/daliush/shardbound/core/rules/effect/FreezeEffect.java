package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;

/** 8.10 Freeze: the rest of this turn and the whole next one, whoever's turns they are; it never shortens a freeze. */
final class FreezeEffect {

    private FreezeEffect() {
    }

    static void apply(Game game, Effect.Freeze freeze, EffectSource source, List<TargetRef> chosen) {
        for (TargetRef target : Targets.resolve(game, freeze.target(), source, chosen)) {
            TargetRef.UnitTarget unitTarget = (TargetRef.UnitTarget) target;
            game.unit(unitTarget.id()).ifPresent(unit -> {
                Unit frozen = unit.frozenThrough(game.turn() + 1);
                game.updateUnit(frozen);
                game.emit(new GameEvent.Frozen(unit.asCard(), frozen.frozenThroughTurn()));
            });
        }
    }
}
