package fr.daliush.shardbound.core.rules.board;

import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Keyword;
import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.rules.trigger.Triggers;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.Relic;
import fr.daliush.shardbound.core.state.Unit;
import java.util.ArrayList;
import java.util.List;

/** Cards arriving on the board: played units and relics (6.3, 6.5) and summoned tokens (8.9). */
public final class Arrivals {

    private Arrivals() {
    }

    public static void unit(Game game, CardInstance card, PlayerId controller) {
        Unit unit = place(game, card, controller, false);
        game.emit(new GameEvent.UnitArrived(card, controller, withAnchor(unit, "6.3", "6.5")));
        Triggers.raise(game, card, controller, unit.arrivalSeq(), Trigger.ARRIVAL);
    }

    public static void relic(Game game, CardInstance card, PlayerId controller) {
        Relic relic = new Relic(card.id(), card.card(), card.owner(), controller, game.newArrivalSeq());
        game.updatePlayer(controller, player -> player.addRelic(relic));
        game.emit(new GameEvent.RelicArrived(card, controller));
        Triggers.raise(game, card, controller, relic.arrivalSeq(), Trigger.ARRIVAL);
    }

    /** Summons one token if there is room; returns whether it arrived. */
    public static boolean token(Game game, CardId token, PlayerId controller) {
        UnitCard card = game.catalog().unit(token);
        if (!BoardSpace.hasRoomFor(card, game.player(controller), game.catalog())) {
            return false;
        }
        CardInstance instance = new CardInstance(game.newInstanceId(), token, controller);
        Unit unit = place(game, instance, controller, true);
        game.emit(new GameEvent.TokenSummoned(instance, controller, withAnchor(unit, "8.9")));
        Triggers.raise(game, instance, controller, unit.arrivalSeq(), Trigger.ARRIVAL);
        return true;
    }

    /** 11.3.1: a unit with Anchor is protected from its arrival. */
    private static Unit place(Game game, CardInstance card, PlayerId controller, boolean token) {
        UnitCard definition = game.catalog().unit(card.card());
        Unit arriving = Unit.arriving(card, controller, token, definition.defense(), game.newArrivalSeq(), game.turn());
        Unit unit = definition.has(Keyword.ANCHOR) ? arriving.withAnchorProtection() : arriving;
        game.updatePlayer(controller, player -> player.addUnit(unit));
        return unit;
    }

    private static List<String> withAnchor(Unit unit, String... rules) {
        List<String> cited = new ArrayList<>(List.of(rules));
        if (unit.anchorProtected()) {
            cited.add("11.3.1");
        }
        return List.copyOf(cited);
    }
}
