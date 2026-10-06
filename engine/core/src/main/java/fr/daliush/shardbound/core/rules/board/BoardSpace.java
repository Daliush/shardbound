package fr.daliush.shardbound.core.rules.board;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.state.PlayerState;

/** Board limits (3.4): 6 places for units, 3 relics. */
public final class BoardSpace {

    private BoardSpace() {
    }

    public static int unitPlacesUsed(PlayerState player, CardCatalog catalog) {
        return player.units().stream().mapToInt(unit -> catalog.unit(unit.card()).size()).sum();
    }

    public static boolean hasRoomFor(UnitCard card, PlayerState player, CardCatalog catalog) {
        return unitPlacesUsed(player, catalog) + card.size() <= PlayerState.MAX_UNIT_PLACES;
    }

    public static boolean hasRoomForRelic(PlayerState player) {
        return player.relics().size() < PlayerState.MAX_RELICS;
    }
}
