package fr.daliush.shardbound.core.rules.board;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;

/** Board limits (3.4): 6 places for units, 3 relics. */
public final class BoardSpace {

    private BoardSpace() {
    }

    public static int unitPlacesUsed(PlayerState player, CardCatalog catalog) {
        return player.units().stream().mapToInt(unit -> places(unit, catalog)).sum();
    }

    public static boolean hasRoomFor(UnitCard card, PlayerState player, CardCatalog catalog) {
        return hasRoomFor(card, player, catalog, List.of());
    }

    /** 3.8: the places of the units sacrificed to play the card are free again when it arrives. */
    public static boolean hasRoomFor(UnitCard card, PlayerState player, CardCatalog catalog,
                                     List<InstanceId> sacrificed) {
        int freed = sacrificed.stream()
                .flatMap(id -> player.unit(id).stream())
                .mapToInt(unit -> places(unit, catalog))
                .sum();
        return unitPlacesUsed(player, catalog) - freed + card.size() <= PlayerState.MAX_UNIT_PLACES;
    }

    public static boolean hasRoomForRelic(PlayerState player) {
        return player.relics().size() < PlayerState.MAX_RELICS;
    }

    private static int places(Unit unit, CardCatalog catalog) {
        return catalog.unit(unit.card()).size();
    }
}
