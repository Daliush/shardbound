package fr.daliush.shardbound.core.bot;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.random.SplitMix64;
import fr.daliush.shardbound.core.view.PlayerView;

/** Picks a legal action uniformly at random, with its own generator: the floor of the bot ranking. */
public final class RandomBot implements Player {

    private final SplitMix64 rng;

    public RandomBot(long seed) {
        this.rng = new SplitMix64(seed);
    }

    @Override
    public Action choose(PlayerView view, Decision decision) {
        return decision.actions().get(rng.nextInt(decision.actions().size()));
    }
}
