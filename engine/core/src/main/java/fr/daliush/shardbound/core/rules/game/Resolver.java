package fr.daliush.shardbound.core.rules.game;

import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.rules.trigger.Abilities;
import fr.daliush.shardbound.core.rules.turn.MainPhase;
import java.util.Optional;

/** Runs the pending work until a player must decide or the game is over (spec §7). */
public final class Resolver {

    private Resolver() {
    }

    public static void run(Game game) {
        while (!game.isOver() && game.pending().isEmpty()) {
            Optional<Step> next = game.peekStep();
            boolean triggersFirst = next.map(Step::waitsForTriggers).orElse(true) && game.hasQueuedTriggers();
            if (triggersFirst) {
                Abilities.start(game, game.pollTrigger());
            } else if (next.isPresent()) {
                StepRunner.run(game, game.popStep());
            } else {
                MainPhase.ask(game);
            }
            StateCheck.run(game);
        }
    }
}
