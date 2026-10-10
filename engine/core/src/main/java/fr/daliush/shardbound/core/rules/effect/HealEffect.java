package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;

/**
 * 8.4 Heal X: up to a unit's max defense, or up to a player's 50 HP. The events report what was restored, 0 for a
 * unit or a player already at their max.
 */
final class HealEffect {

    private HealEffect() {
    }

    static void apply(Game game, Effect.Heal heal, EffectSource source, List<TargetRef> chosen) {
        for (TargetRef target : Targets.resolve(game, heal.target(), source, chosen)) {
            switch (target) {
                case TargetRef.UnitTarget unitTarget -> game.unit(unitTarget.id()).ifPresent(unit -> {
                    Unit healed = unit.healed(heal.amount());
                    game.updateUnit(healed);
                    game.emit(new GameEvent.UnitHealed(unit.asCard(), healed.defense() - unit.defense()));
                });
                case TargetRef.PlayerTarget player -> {
                    int hp = game.player(player.player()).hp();
                    int restored = Math.max(0, Math.min(PlayerState.MAX_HP, hp + heal.amount()) - hp);
                    game.updatePlayer(player.player(), state -> state.withHp(hp + restored));
                    game.emit(new GameEvent.PlayerHealed(player.player(), restored));
                }
                default -> throw new IllegalStateException("Heal cannot hit " + target);
            }
        }
    }
}
