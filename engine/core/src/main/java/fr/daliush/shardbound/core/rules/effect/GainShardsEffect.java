package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.GainMode;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.Shards;

/** 8.12 Gain Shards: Shards for this turn only, or one more max Shard (up to 10), for the effect's controller. */
final class GainShardsEffect {

    private GainShardsEffect() {
    }

    static void apply(Game game, Effect.GainShards gain, EffectSource source) {
        PlayerId player = source.controller();
        Shards before = game.player(player).shards();
        Shards after = switch (gain.mode()) {
            case THIS_TURN -> before.gained(gain.amount());
            case MAX -> before.withMaxRaised();
        };
        game.updatePlayer(player, state -> state.withShards(after));
        game.emit(new GameEvent.ShardsGained(player, gained(before, after, gain.mode()), gain.mode()));
    }

    /** What the player got: Shards for this turn, or a max Shard (none above the cap). */
    private static int gained(Shards before, Shards after, GainMode mode) {
        return mode == GainMode.THIS_TURN ? after.available() - before.available() : after.max() - before.max();
    }
}
