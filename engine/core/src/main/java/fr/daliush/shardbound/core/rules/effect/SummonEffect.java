package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.rules.board.Arrivals;
import fr.daliush.shardbound.core.rules.game.Game;

/** 8.9 Summon: tokens arrive until the board is full; then nothing happens (3.4). */
final class SummonEffect {

    private SummonEffect() {
    }

    static void apply(Game game, Effect.Summon summon, EffectSource source) {
        for (int i = 0; i < summon.count(); i++) {
            if (!Arrivals.token(game, summon.token(), source.controller())) {
                game.emit(new GameEvent.SummonFailed(source.controller(), summon.token()));
                return;
            }
        }
    }
}
