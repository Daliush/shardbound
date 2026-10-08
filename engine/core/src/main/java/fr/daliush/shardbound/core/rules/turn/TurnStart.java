package fr.daliush.shardbound.core.rules.turn;

import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.rules.trigger.Triggers;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.state.Shards;
import fr.daliush.shardbound.core.state.Unit;

/** 5.2: the start of a player's turn, in the rulebook's order. */
public final class TurnStart {

    private TurnStart() {
    }

    public static void run(Game game, PlayerId player) {
        game.startTurnOf(player);
        game.emit(new GameEvent.TurnStarted(player, game.turn()));
        thaw(game);
        endAnchorProtection(game, player);
        clearTurnFlags(game);
        refillShards(game, player);
        boolean firstTurnOfTheGame = player == game.firstPlayer() && game.player(player).turnsTaken() == 0;
        game.updatePlayer(player, state -> state.withTurnsTaken(state.turnsTaken() + 1));
        if (!firstTurnOfTheGame) {
            CardDraws.draw(game, player, "5.2.3");
        }
        Triggers.raiseForBoard(game, player, Trigger.TURN_START);
    }

    /** 8.10: first, the units whose freeze ended with the last turn thaw, on both boards. */
    private static void thaw(Game game) {
        for (Unit unit : game.unitsByArrival()) {
            if (unit.frozenThroughTurn() > 0 && !unit.isFrozen(game.turn())) {
                game.updateUnit(unit.thawed());
                game.emit(new GameEvent.UnitThawed(unit.asCard()));
            }
        }
    }

    /** 5.2.1: the active player's units lose their Anchor protection (11.3.1). */
    private static void endAnchorProtection(Game game, PlayerId player) {
        for (Unit unit : game.player(player).units()) {
            if (unit.anchorProtected()) {
                game.updateUnit(unit.withAnchorProtectionEnded());
                game.emit(new GameEvent.AnchorProtectionEnded(unit.asCard()));
            }
        }
    }

    private static void clearTurnFlags(Game game) {
        for (PlayerId id : PlayerId.values()) {
            game.updatePlayer(id, state -> state.withUnits(
                    state.units().stream().map(Unit::withTurnFlagsCleared).toList()));
        }
    }

    /** 5.2.2. */
    private static void refillShards(Game game, PlayerId player) {
        PlayerState state = game.player(player);
        Shards refilled = state.shards().refilled();
        game.updatePlayer(player, p -> p.withShards(refilled));
        game.emit(new GameEvent.ShardsRefilled(player, refilled.max(), refilled.available(),
                state.shards().lockedNextTurn()));
    }
}
