package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.Modifier;
import java.util.List;

/** 8.5 Modify +X/+Y: X on the unit's attack damage, Y on its max and current defense, for good or until end of turn. */
final class ModifyEffect {

    private ModifyEffect() {
    }

    static void apply(Game game, Effect.Modify modify, EffectSource source, List<TargetRef> chosen) {
        Modifier modifier = new Modifier(modify.attackDamage(), modify.defense(), modify.duration(), source.instance());
        for (TargetRef target : Targets.resolve(game, modify.target(), source, chosen)) {
            TargetRef.UnitTarget unitTarget = (TargetRef.UnitTarget) target;
            game.unit(unitTarget.id()).ifPresent(unit -> {
                game.updateUnit(unit.withModifier(modifier));
                game.emit(new GameEvent.Modified(unit.asCard(), modify.attackDamage(), modify.defense(),
                        modify.duration()));
            });
        }
    }
}
