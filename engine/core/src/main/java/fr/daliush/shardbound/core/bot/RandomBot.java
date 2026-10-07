package fr.daliush.shardbound.core.bot;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.random.SplitMix64;
import fr.daliush.shardbound.core.view.PlayerView;

/** Picks a legal action uniformly at random, with its own generator: the floor of the bot ranking. */
public final class RandomBot implements Bot {

    private final SplitMix64 rng;

    /** {@code new RandomBot(bot.rngState())} picks exactly what {@code bot} would pick next. */
    public RandomBot(long seed) {
        this.rng = new SplitMix64(seed);
    }

    @Override
    public Action choose(PlayerView view, Decision decision) {
        return decision.actions().get(rng.nextInt(decision.actions().size()));
    }

    @Override
    public long rngState() {
        return rng.state();
    }
}
