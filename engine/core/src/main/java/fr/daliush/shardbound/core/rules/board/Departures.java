package fr.daliush.shardbound.core.rules.board;

import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.rules.trigger.Echoes;
import fr.daliush.shardbound.core.rules.trigger.Triggers;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.Relic;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;

/**
 * Cards leaving the board: by dying (9.3, 9.4), or back to their owner's hand (8.8). A token vanishes instead of
 * going anywhere (3.6). Nothing makes an anchored unit leave (11.3.2); a unit that leaves breaks its link (11.5.4).
 */
public final class Departures {

    private Departures() {
    }

    public static void destroy(Game game, Unit unit, List<String> rules) {
        if (!staysAnchored(game, unit, GameEvent.Removal.DESTROY)) {
            die(game, unit, new GameEvent.UnitDestroyed(unit.asCard(), unit.controller(), rules));
        }
    }

    /** 8.3: a sacrificed unit dies, like a destroyed one. An anchored one counts as sacrificed but stays (11.3.3). */
    public static void sacrifice(Game game, Unit unit, List<String> rules) {
        if (!staysAnchored(game, unit, GameEvent.Removal.SACRIFICE)) {
            die(game, unit, new GameEvent.UnitSacrificed(unit.asCard(), unit.controller(), rules));
        }
    }

    public static void destroy(Game game, Relic relic) {
        game.updatePlayer(relic.controller(), player -> player.removeRelic(relic.id()));
        game.emit(new GameEvent.RelicDestroyed(relic.asCard(), relic.controller()));
        game.updatePlayer(relic.owner(), player -> player.addToGraveyard(relic.asCard()));
        Triggers.raise(game, relic.asCard(), relic.controller(), relic.arrivalSeq(), Trigger.DEATH);
        Triggers.raise(game, relic.asCard(), relic.controller(), relic.arrivalSeq(), Trigger.DEPARTURE);
    }

    /** 8.8: the unit does not die, so only its "Departure" abilities trigger. */
    public static void returnToHand(Game game, Unit unit) {
        if (staysAnchored(game, unit, GameEvent.Removal.RETURN_TO_HAND)) {
            return;
        }
        game.updatePlayer(unit.controller(), player -> player.removeUnit(unit.id()));
        if (unit.token()) {
            game.emit(new GameEvent.TokenVanished(unit.asCard(), List.of("8.8", "3.6")));
        } else {
            toOwnersHand(game, unit.asCard());
        }
        breakLink(game, unit);
        Triggers.raise(game, unit.asCard(), unit.controller(), unit.arrivalSeq(), Trigger.DEPARTURE);
    }

    public static void returnToHand(Game game, Relic relic) {
        game.updatePlayer(relic.controller(), player -> player.removeRelic(relic.id()));
        toOwnersHand(game, relic.asCard());
        Triggers.raise(game, relic.asCard(), relic.controller(), relic.arrivalSeq(), Trigger.DEPARTURE);
    }

    /** 11.5.4: when either unit leaves the board, the link breaks. */
    private static void breakLink(Game game, Unit unit) {
        unit.linkedTo().flatMap(game::unit).ifPresent(partner -> {
            game.updateUnit(partner.withLinkBroken());
            game.emit(new GameEvent.LinkBroken(unit.asCard(), partner.asCard()));
        });
    }

    /** 11.3.2, 11.3.3: only the part that would make the unit leave is ignored. */
    private static boolean staysAnchored(Game game, Unit unit, GameEvent.Removal attempt) {
        if (unit.anchorProtected()) {
            game.emit(new GameEvent.AnchorPrevented(unit.asCard(), attempt));
        }
        return unit.anchorProtected();
    }

    /** 6.7: the same instance, reset; into a full hand (3.3), the graveyard instead. */
    private static void toOwnersHand(Game game, CardInstance card) {
        if (game.player(card.owner()).handIsFull()) {
            game.updatePlayer(card.owner(), player -> player.addToGraveyard(card));
            game.emit(new GameEvent.SentToGraveyardHandFull(card));
        } else {
            game.updatePlayer(card.owner(), player -> player.addToHand(HandCard.fresh(card)));
            game.emit(new GameEvent.ReturnedToHand(card));
        }
    }

    private static void die(Game game, Unit unit, GameEvent death) {
        game.updatePlayer(unit.controller(), player -> player.removeUnit(unit.id()));
        game.emit(death);
        if (unit.token()) {
            game.emit(new GameEvent.TokenVanished(unit.asCard()));
        } else {
            game.updatePlayer(unit.owner(), player -> player.addToGraveyard(unit.asCard()));
        }
        breakLink(game, unit);
        Echoes.raise(game, unit);
        Triggers.raise(game, unit.asCard(), unit.controller(), unit.arrivalSeq(), Trigger.DEATH);
        Triggers.raise(game, unit.asCard(), unit.controller(), unit.arrivalSeq(), Trigger.DEPARTURE);
    }
}
