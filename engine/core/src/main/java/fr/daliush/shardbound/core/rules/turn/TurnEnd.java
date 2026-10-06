package fr.daliush.shardbound.core.rules.turn;

import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.rules.trigger.Triggers;
import fr.daliush.shardbound.core.state.GameResult;
import fr.daliush.shardbound.core.state.PlayerId;
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

    public static void finish(Game game, PlayerId player) {
        game.updatePlayer(player, state -> state.withShards(state.shards().emptied()));
        game.emit(new GameEvent.TurnEnded(player));
        if (game.turn() >= LAST_TURN) {
            game.end(new GameResult.Draw(GameResult.EndReason.TURN_LIMIT), List.of("1.5"));
        } else {
            game.push(new Step.StartTurn(player.opponent()));
        }
    }
}
