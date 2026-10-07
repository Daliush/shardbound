package fr.daliush.shardbound.core.rules.turn;

import fr.daliush.shardbound.core.content.Duration;
import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.rules.board.Departures;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.rules.trigger.Triggers;
import fr.daliush.shardbound.core.state.GameResult;
import fr.daliush.shardbound.core.state.Modifier;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;

/** 5.4: the end of a turn, then the next player's turn; or a draw after each player's 50th turn (1.5). */
public final class TurnEnd {

    public static final int LAST_TURN = 100;

    private TurnEnd() {
    }

    /** 5.4.1. The rest waits until these abilities have resolved. */
    public static void triggerAbilities(Game game, PlayerId player) {
        Triggers.raiseForBoard(game, player, Trigger.TURN_END);
        game.push(new Step.FinishTurn(player));
    }

    /** 5.4.2 and 5.4.3. The rest waits until the abilities they trigger have resolved. */
    public static void finish(Game game, PlayerId player) {
        endTemporaryModifiers(game);
        destroyDoomedUnits(game, player);
        game.push(new Step.PassTurn(player));
    }

    /** 5.4.4, then the opponent's turn, or a draw after each player's 50th turn (1.5). */
    public static void pass(Game game, PlayerId player) {
        game.updatePlayer(player, state -> state.withShards(state.shards().emptied()));
        game.emit(new GameEvent.TurnEnded(player));
        if (game.turn() >= LAST_TURN) {
            game.end(new GameResult.Draw(GameResult.EndReason.TURN_LIMIT), List.of("1.5"));
        } else {
            game.push(new Step.StartTurn(player.opponent()));
        }
    }

    /**
     * 5.4.3, 11.3.5: the active player's doomed units still at 0 defense are destroyed, once their protection has
     * ended. One doomed during its arrival turn is still protected, so it gets through that turn.
     */
    private static void destroyDoomedUnits(Game game, PlayerId player) {
        for (Unit unit : game.player(player).units()) {
            if (unit.doomed() && !unit.anchorProtected() && unit.defense() == 0) {
                Departures.destroy(game, unit, List.of("5.4.3", "11.3.5"));
            }
        }
    }

    /** 5.4.2: every "until end of turn" modifier ends now, whoever's turn it is and whoever's unit it is (8.19). */
    private static void endTemporaryModifiers(Game game) {
        for (Unit unit : game.unitsByArrival()) {
            Unit changed = unit;
            for (Modifier modifier : unit.modifiers()) {
                if (modifier.duration() == Duration.END_OF_TURN) {
                    changed = changed.withModifierEnded(modifier);
                    game.emit(new GameEvent.ModifierExpired(unit.asCard(), modifier.attackDamage(),
                            modifier.defense()));
                }
            }
            game.updateUnit(changed);
        }
    }
}
