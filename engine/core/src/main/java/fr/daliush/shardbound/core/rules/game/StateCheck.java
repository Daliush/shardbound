package fr.daliush.shardbound.core.rules.game;

import fr.daliush.shardbound.core.rules.board.Departures;
import fr.daliush.shardbound.core.rules.trigger.TriggerOrder;
import fr.daliush.shardbound.core.state.GameResult;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;

/**
 * Runs after every step: units at 0 defense die together (6.6), the abilities raised by the step join the
 * queue in order (9.8, 9.10), and a player at 0 HP ends the game (1.2, 1.3, 1.6).
 */
public final class StateCheck {

    private StateCheck() {
    }

    public static void run(Game game) {
        if (game.isOver()) {
            return;
        }
        List<Unit> dying = unitsAtZero(game);
        while (!dying.isEmpty()) {
            dying.forEach(unit -> Departures.destroy(game, unit, List.of("6.6")));
            dying = unitsAtZero(game);
        }
        game.enqueue(TriggerOrder.sorted(game.takeRaised(), game.active()));
        endIfAPlayerIsDown(game);
    }

    private static List<Unit> unitsAtZero(Game game) {
        return game.unitsByArrival().stream().filter(unit -> unit.defense() <= 0).toList();
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
