package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;

/** 8.1 Deal X damage. Inside an attack, the attacker's bonuses apply (8.18); never below 0 (8.15). */
final class DamageEffect {

    private DamageEffect() {
    }

    static void apply(Game game, Effect.Damage damage, EffectSource source, List<TargetRef> chosen) {
        int amount = damage.withBonus(attackBonus(game, source));
        for (TargetRef target : Targets.resolve(game, damage.target(), source, chosen)) {
            switch (target) {
                case TargetRef.UnitTarget unitTarget -> game.unit(unitTarget.id()).ifPresent(unit -> {
                    game.updateUnit(unit.damaged(amount));
                    game.emit(new GameEvent.UnitDamaged(unit.asCard(), amount));
                });
                case TargetRef.PlayerTarget player -> {
                    game.updatePlayer(player.player(), state -> state.withHp(state.hp() - amount));
                    game.emit(new GameEvent.PlayerDamaged(player.player(), amount));
                }
                default -> throw new IllegalStateException("Damage cannot hit " + target);
            }
        }
    }

    private static int attackBonus(Game game, EffectSource source) {
        return source.isAttack() ? game.unit(source.instance()).map(Unit::attackBonus).orElse(0) : 0;
    }
}
