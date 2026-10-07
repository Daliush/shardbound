package fr.daliush.shardbound.core.rules.effect;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.rules.board.BoardSpace;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.state.Unit;
import java.util.Comparator;
import java.util.List;

/**
 * Sacrifices (8.3, 8.16), counted in one place: a unit counts as many sacrifices as the board places it takes,
 * which is one until units get a size.
 */
public final class Sacrifices {

    private Sacrifices() {
    }

    public static int available(PlayerState player, CardCatalog catalog) {
        return BoardSpace.unitPlacesUsed(player, catalog);
    }

    /** 8.16: a sacrifice is never partial. */
    public static boolean canMake(PlayerState player, CardCatalog catalog, int count) {
        return available(player, catalog) >= count;
    }

    /** What the Sacrifice effects of a list ask for. */
    public static int askedBy(List<Effect> effects) {
        return effects.stream()
                .filter(Effect.Sacrifice.class::isInstance)
                .mapToInt(effect -> ((Effect.Sacrifice) effect).count())
                .sum();
    }

    /** Every way to pick the player's units for {@code count} sacrifices, by arrival; none when they cannot. */
    public static List<List<InstanceId>> options(PlayerState player, CardCatalog catalog, int count) {
        List<Unit> units = player.units().stream().sorted(Comparator.comparingInt(Unit::arrivalSeq)).toList();
        return Combinations.withTotal(units, unit -> catalog.unit(unit.card()).size(), count).stream()
                .map(picked -> picked.stream().map(Unit::id).toList())
                .toList();
    }
}
