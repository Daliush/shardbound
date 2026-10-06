package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.PlayerState;
import java.util.List;

/** 8.4 Heal X: up to a unit's max defense, or up to a player's 50 HP. */
final class HealEffect {

    private HealEffect() {
    }

    static void apply(Game game, Effect.Heal heal, EffectSource source, List<TargetRef> chosen) {
        for (TargetRef target : Targets.resolve(game, heal.target(), source, chosen)) {
            switch (target) {
                case TargetRef.UnitTarget unitTarget -> game.unit(unitTarget.id()).ifPresent(unit -> {
                    game.updateUnit(unit.healed(heal.amount()));
                    game.emit(new GameEvent.UnitHealed(unit.asCard(), heal.amount()));
                });
                case TargetRef.PlayerTarget player -> {
                    game.updatePlayer(player.player(),
                            state -> state.withHp(Math.min(PlayerState.MAX_HP, state.hp() + heal.amount())));
                    game.emit(new GameEvent.PlayerHealed(player.player(), heal.amount()));
                }
                default -> throw new IllegalStateException("Heal cannot hit " + target);
            }
        }
    }
}
