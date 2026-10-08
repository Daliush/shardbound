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

/**
 * The outcome of a scenario: the final state, every decision met (one per choice played, in order), and the events,
 * unredacted, in the order the rules applied. The events are the answer key: each names the rule IDs applied.
 */
public record ScenarioResult(GameState state, List<GameEvent> events, List<Decision> decisions) {

    public ScenarioResult {
        events = List.copyOf(events);
        decisions = List.copyOf(decisions);
    }

    public PlayerState player(PlayerId id) {
        return state.player(id);
    }

    /** The first unit with this card on either board, oldest arrival first; it must be there. */
    public Unit unit(String card) {
        return findUnit(card).orElseThrow(() -> new IllegalStateException("No unit " + card + " on the board"));
    }

    /** The first unit with this card on either board, oldest arrival first, if any is left. */
    public Optional<Unit> findUnit(String card) {
        return state.unitsByArrival().stream().filter(unit -> unit.card().equals(new CardId(card))).findFirst();
    }

    /** The decision the game waits for at the end of the scenario. */
    public Optional<Decision> pending() {
        return state.isOver() ? Optional.empty() : state.pending();
    }

    /** The events of one type, in order: {@code events(GameEvent.UnitDamaged.class)}. */
    public <E extends GameEvent> List<E> events(Class<E> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /**
     * The rule trace as one line per event, {@code "UnitDamaged[8.1]"}: the event's type and the rule IDs applied, in
     * order. One list checks both the order of the steps and the rules cited.
     */
    public List<String> trace() {
        return events.stream().map(event -> event.getClass().getSimpleName() + event.rules()).toList();
    }
}
