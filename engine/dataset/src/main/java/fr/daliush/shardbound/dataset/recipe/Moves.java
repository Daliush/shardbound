package fr.daliush.shardbound.dataset.recipe;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.InstanceId;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

/** Recognizes a legal action by the cards it involves, for a question that asks whether a move is legal. */
public final class Moves {

    private Moves() {
    }

    /** Any play of this card, whatever its targets. */
    public static BiPredicate<GameState, Action> playing(String card) {
        return (state, action) -> action instanceof Action.PlayCard play && isInHand(state, play.card(), card);
    }

    /** An attack with this attack ability of a unit with this card, whatever its target. */
    public static BiPredicate<GameState, Action> attackingWith(String unit, int attackIndex) {
        return (state, action) -> action instanceof Action.Attack attack && attack.attackIndex() == attackIndex
                && isUnit(state, attack.attacker(), unit);
    }

    /** An intercept by a unit with this card (7.5). */
    public static BiPredicate<GameState, Action> interceptingWith(String unit) {
        return (state, action) -> action instanceof Action.Intercept intercept
                && isUnit(state, intercept.interceptor(), unit);
    }

    private static boolean isUnit(GameState state, InstanceId id, String card) {
        return state.unit(id).map(unit -> unit.card().equals(new CardId(card))).orElse(false);
    }

    private static boolean isInHand(GameState state, InstanceId id, String card) {
        return Stream.of(state.p1(), state.p2()).flatMap(player -> player.hand().stream())
                .anyMatch(inHand -> inHand.id().equals(id) && inHand.card().card().equals(new CardId(card)));
    }
}
