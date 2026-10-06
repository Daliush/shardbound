package fr.daliush.shardbound.core.rules.combat;

import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.Unit;
import java.util.Comparator;
import java.util.List;

/** 7.5: the defender's other units that are not frozen and have not intercepted this turn. */
public final class Interceptors {

    private Interceptors() {
    }

    public static List<Unit> eligible(Game game, PlayerId defender, Unit target) {
        return game.player(defender).units().stream()
                .filter(unit -> !unit.id().equals(target.id()))
                .filter(unit -> !unit.isFrozen(game.turn()))
                .filter(unit -> !unit.hasInterceptedThisTurn())
                .sorted(Comparator.comparingInt(Unit::arrivalSeq))
                .toList();
    }
}
