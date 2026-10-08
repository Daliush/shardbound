package fr.daliush.shardbound.core.rules.game;

import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.aura.StatAuras;
import fr.daliush.shardbound.core.rules.board.Departures;
import fr.daliush.shardbound.core.rules.trigger.TriggerOrder;
import fr.daliush.shardbound.core.state.GameResult;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;

/**
 * Runs after every step: stat auras apply (8.14), units at 0 defense die together (6.6) unless Anchor dooms them
 * (11.3.4), the abilities raised by the step join the queue in order (9.8, 9.10), and a player at 0 HP ends the game
 * (1.2, 1.3, 1.6).
 */
public final class StateCheck {

    private StateCheck() {
    }

    public static void run(Game game) {
        if (game.isOver()) {
            return;
        }
        StatAuras.reconcile(game);
        liftDoom(game);
        List<Unit> dying = unitsToDestroy(game);
        while (!dying.isEmpty()) {
            dying.forEach(unit -> Departures.destroy(game, unit, List.of("6.6")));
            // A dead aura card stops applying: its malus may give defense back, never take more.
            StatAuras.reconcile(game);
            dying = unitsToDestroy(game);
        }
        game.enqueue(TriggerOrder.sorted(game.takeRaised(), game.active()));
        endIfAPlayerIsDown(game);
    }

    /** 11.3.4: a doomed unit whose defense went back above 0 is no longer doomed. */
    private static void liftDoom(Game game) {
        for (Unit unit : game.unitsByArrival()) {
            if (unit.doomed() && unit.defense() > 0) {
                game.updateUnit(unit.withDoomLifted());
                game.emit(new GameEvent.DoomLifted(unit.asCard()));
            }
        }
    }

    /** 6.6: the units at 0 defense, once the anchored ones are doomed; a doomed unit waits for 5.4.3 (11.3.5). */
    private static List<Unit> unitsToDestroy(Game game) {
        doomAnchoredUnitsAtZero(game);
        return game.unitsByArrival().stream().filter(unit -> unit.defense() <= 0 && !unit.doomed()).toList();
    }

    /** 11.3.4: a protected unit at 0 defense stays on the board, doomed. */
    private static void doomAnchoredUnitsAtZero(Game game) {
        for (Unit unit : game.unitsByArrival()) {
            if (unit.defense() <= 0 && unit.anchorProtected() && !unit.doomed()) {
                game.updateUnit(unit.markDoomed());
                game.emit(new GameEvent.UnitDoomed(unit.asCard()));
            }
        }
    }

    private static void endIfAPlayerIsDown(Game game) {
        boolean p1Down = game.player(PlayerId.P1).hp() <= 0;
        boolean p2Down = game.player(PlayerId.P2).hp() <= 0;
        if (p1Down && p2Down) {
            game.end(new GameResult.Draw(GameResult.EndReason.DOUBLE_KO), List.of("1.3", "1.6"));
        } else if (p1Down || p2Down) {
            PlayerId winner = p1Down ? PlayerId.P2 : PlayerId.P1;
            game.end(new GameResult.Win(winner, GameResult.EndReason.HP), List.of("1.2", "1.6"));
        }
    }
}
