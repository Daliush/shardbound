package fr.daliush.shardbound.core.bot.greedy;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;
import java.util.Optional;

/**
 * What an action is worth to the bot once it has resolved (spec §10). {@code apply} stops at the next decision, which
 * can be a choice inside the same resolution: an intercept, or a target for an ability the action triggered. Until
 * the next main decision or the end of the game, the bot answers its own choices greedily, and the opponent takes
 * their first option: no intercept, the first target.
 */
final class ActionOutcome {

    private final GameEngine engine;
    private final Evaluator evaluator;
    private final PlayerId bot;

    ActionOutcome(GameEngine engine, Evaluator evaluator, PlayerId bot) {
        this.engine = engine;
        this.evaluator = evaluator;
        this.bot = bot;
    }

    /** The bot's best option in {@code world}; the lowest index wins a tie. */
    ScoredAction best(GameState world, List<Action> options) {
        ScoredAction best = new ScoredAction(options.getFirst(), score(world, options.getFirst()));
        for (Action option : options.subList(1, options.size())) {
            double score = score(world, option);
            if (score > best.score()) {
                best = new ScoredAction(option, score);
            }
        }
        return best;
    }

    double score(GameState world, Action action) {
        GameState after = engine.apply(world, action).state();
        Optional<Decision> next = engine.decision(after);
        if (next.isEmpty() || !next.get().kind().pausesAStep()) {
            return evaluator.score(after, bot);
        }
        if (next.get().player() == bot) {
            return best(after, next.get().actions()).score();
        }
        return score(after, next.get().actions().getFirst());
    }

    record ScoredAction(Action action, double score) {}
}
