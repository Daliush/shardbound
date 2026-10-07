package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.board.Departures;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import java.util.List;

/** 8.3 Sacrifice: its controller picks which of their units die. A sacrifice is never partial (8.16, 8.22). */
final class SacrificeEffect {

    private SacrificeEffect() {
    }

    /** The ways to make the sacrifice; none when it can no longer be made. */
    static List<List<InstanceId>> options(Game game, Effect.Sacrifice sacrifice, PlayerId controller) {
        return Sacrifices.options(game.player(controller), game.catalog(), sacrifice.count());
    }

    /** 8.22: neither this sacrifice nor the effects printed after it apply. */
    static void fail(Game game, Effect.Sacrifice sacrifice, PlayerId controller) {
        PlayerState player = game.player(controller);
        game.emit(new GameEvent.SacrificeFailed(controller, sacrifice.count(),
                Sacrifices.available(player, game.catalog())));
    }

    /** The units die together: the state check runs once they are all gone. */
    static void apply(Game game, List<InstanceId> units) {
        for (InstanceId id : units) {
            game.unit(id).ifPresent(unit -> Departures.sacrifice(game, unit, List.of("8.3")));
        }
    }
}
