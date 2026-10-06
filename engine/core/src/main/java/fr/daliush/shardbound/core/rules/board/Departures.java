package fr.daliush.shardbound.core.rules.board;

import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.rules.trigger.Triggers;
import fr.daliush.shardbound.core.state.Relic;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;

/** Cards leaving the board by dying (9.3, 9.4). A token vanishes instead of going to the graveyard (3.6). */
public final class Departures {

    private Departures() {
    }

    public static void destroy(Game game, Unit unit, List<String> rules) {
        game.updatePlayer(unit.controller(), player -> player.removeUnit(unit.id()));
        game.emit(new GameEvent.UnitDestroyed(unit.asCard(), unit.controller(), rules));
        if (unit.token()) {
            game.emit(new GameEvent.TokenVanished(unit.asCard()));
        } else {
            game.updatePlayer(unit.owner(), player -> player.addToGraveyard(unit.asCard()));
        }
        Triggers.raise(game, unit.asCard(), unit.controller(), unit.arrivalSeq(), Trigger.DEATH);
        Triggers.raise(game, unit.asCard(), unit.controller(), unit.arrivalSeq(), Trigger.DEPARTURE);
    }

    public static void destroy(Game game, Relic relic) {
        game.updatePlayer(relic.controller(), player -> player.removeRelic(relic.id()));
        game.emit(new GameEvent.RelicDestroyed(relic.asCard(), relic.controller()));
        game.updatePlayer(relic.owner(), player -> player.addToGraveyard(relic.asCard()));
        Triggers.raise(game, relic.asCard(), relic.controller(), relic.arrivalSeq(), Trigger.DEATH);
        Triggers.raise(game, relic.asCard(), relic.controller(), relic.arrivalSeq(), Trigger.DEPARTURE);
    }
}
