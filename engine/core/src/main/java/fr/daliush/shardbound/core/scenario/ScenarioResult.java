package fr.daliush.shardbound.core.scenario;

import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;
import java.util.Optional;

/** The outcome of a scenario: the final state, every decision met, and the unredacted rule trace. */
public record ScenarioResult(GameState state, List<GameEvent> events, List<Decision> decisions) {

    public ScenarioResult {
        events = List.copyOf(events);
        decisions = List.copyOf(decisions);
    }

    public PlayerState player(PlayerId id) {
        return state.player(id);
    }

    public Unit unit(String card) {
        return findUnit(card).orElseThrow(() -> new IllegalStateException("No unit " + card + " on the board"));
    }

    public Optional<Unit> findUnit(String card) {
        return state.unitsByArrival().stream().filter(unit -> unit.card().equals(new CardId(card))).findFirst();
    }

    /** The decision the game waits for at the end of the scenario. */
    public Optional<Decision> pending() {
        return state.isOver() ? Optional.empty() : state.pending();
    }

    public <E extends GameEvent> List<E> events(Class<E> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }
}
