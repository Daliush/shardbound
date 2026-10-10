package fr.daliush.shardbound.dataset.recipe;

import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.state.GameState;
import java.util.List;
import java.util.function.Function;

/** Facts about a scenario's final state, for {@link Answer.Events#finalState()}. */
public final class StateFacts {

    private StateFacts() {
    }

    /** "Shardling has 3 defense, out of 3.", or "Shardling is no longer on the board." */
    public static Function<GameState, List<String>> defenseOf(String card, String name) {
        return state -> List.of(state.unitsByArrival().stream()
                .filter(unit -> unit.card().equals(new CardId(card)))
                .findFirst()
                .map(unit -> name + " has " + unit.defense() + " defense, out of " + unit.maxDefense() + ".")
                .orElse(name + " is no longer on the board."));
    }
}
