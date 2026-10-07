package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;

/** 8.11 Link: two units, allied or enemy, each still without a link (11.5.1), are linked to each other. */
final class LinkEffect {

    private LinkEffect() {
    }

    /** A pair that is no longer valid, because a unit left or got linked in the meantime, does nothing (10.5). */
    static void apply(Game game, List<TargetRef> chosen) {
        if (!TargetOptions.linkPairs(game).contains(chosen)) {
            return;
        }
        Unit first = unit(game, chosen.get(0));
        Unit second = unit(game, chosen.get(1));
        game.updateUnit(first.linkedWith(second.id()));
        game.updateUnit(second.linkedWith(first.id()));
        game.emit(new GameEvent.Linked(first.asCard(), second.asCard()));
    }

    private static Unit unit(Game game, TargetRef target) {
        return game.unit(((TargetRef.UnitTarget) target).id()).orElseThrow();
    }
}
