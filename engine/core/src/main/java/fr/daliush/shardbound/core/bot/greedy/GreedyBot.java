package fr.daliush.shardbound.core.bot.greedy;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.bot.Bot;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.determinization.Determinizer;
import fr.daliush.shardbound.core.random.SplitMix64;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.view.PlayerView;

/**
 * Picks the action that leaves the best board, one action ahead (spec §10). It sees only its view, like any player:
 * the engine only serves to apply each option to a game the bot determinized itself, never to the real one. The
 * package's only entry point.
 */
public final class GreedyBot implements Bot {

    private final GameEngine engine;
    private final Determinizer determinizer;
    private final SplitMix64 rng;

    /** {@code new GreedyBot(engine, bot.rngState())} picks exactly what {@code bot} would pick next. */
    public GreedyBot(GameEngine engine, long rngState) {
        this.engine = engine;
        this.determinizer = new Determinizer(engine.catalog());
        this.rng = new SplitMix64(rngState);
    }

    /**
     * <ol>
     *     <li>a single option is taken as it is: there is nothing to weigh;</li>
     *     <li>otherwise, one game is determinized from the view, with the bot's own generator;</li>
     *     <li>a mulligan follows the opening hand rule ({@link OpeningHand});</li>
     *     <li>any other decision takes the option whose outcome scores best for the bot ({@link ActionOutcome},
     *     {@link Evaluator}), the lowest index on a tie.</li>
     * </ol>
     */
    @Override
    public Action choose(PlayerView view, Decision decision) {
        if (decision.actions().size() == 1) {
            return decision.actions().getFirst();
        }
        GameState world = determinizer.determinize(view, rng.nextLong());
        if (decision.kind() == DecisionKind.MULLIGAN) {
            return OpeningHand.answer(world, decision, engine.catalog());
        }
        return new ActionOutcome(engine, new Evaluator(engine.catalog()), view.viewer())
                .best(world, decision.actions())
                .action();
    }

    @Override
    public long rngState() {
        return rng.state();
    }
}
