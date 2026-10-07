package fr.daliush.shardbound.core.scenario;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.Unit;
import java.util.stream.Stream;

/** Names a target by card rather than by instance id, so scenarios read like the rules. */
@FunctionalInterface
public interface Pick {

    TargetRef in(GameState state);

    /** The {@code nth} unit with this card on the board, oldest arrival first (1 for the first). */
    static Pick unit(String card, int nth) {
        return state -> TargetRef.unit(nthUnit(state, card, nth).id());
    }

    static Pick unit(String card) {
        return unit(card, 1);
    }

    /** The first relic with this card on either board. */
    static Pick relic(String card) {
        return state -> Stream.of(state.p1(), state.p2())
                .flatMap(player -> player.relics().stream())
                .filter(relic -> relic.card().equals(new CardId(card)))
                .findFirst()
                .<TargetRef>map(relic -> new TargetRef.RelicTarget(relic.id()))
                .orElseThrow(() -> new IllegalStateException("No relic with card " + card));
    }

    /** The oldest card with this card id in either graveyard (8.13). */
    static Pick graveyardCard(String card) {
        return state -> Stream.of(state.p1(), state.p2())
                .flatMap(player -> player.graveyard().stream())
                .filter(inGraveyard -> inGraveyard.card().equals(new CardId(card)))
                .findFirst()
                .<TargetRef>map(inGraveyard -> new TargetRef.GraveyardCardTarget(inGraveyard.id()))
                .orElseThrow(() -> new IllegalStateException("No " + card + " in a graveyard"));
    }

    static Pick player(PlayerId player) {
        return state -> TargetRef.player(player);
    }

    static Unit nthUnit(GameState state, String card, int nth) {
        return state.unitsByArrival().stream()
                .filter(unit -> unit.card().equals(new CardId(card)))
                .skip(nth - 1L)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No unit #" + nth + " with card " + card));
    }
}
