package fr.daliush.shardbound.core.rules.game;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.random.SplitMix64;
import fr.daliush.shardbound.core.resolution.QueuedTrigger;
import fr.daliush.shardbound.core.resolution.Resolution;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.state.GameResult;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.state.Relic;
import fr.daliush.shardbound.core.state.Unit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The game being worked on during one engine call: a mutable copy of a {@link GameState}, the events
 * emitted so far, and the abilities raised by the current step. The engine freezes it back into a state.
 */
public final class Game {

    private final CardCatalog catalog;
    private final PlayerId firstPlayer;
    private final Map<PlayerId, PlayerState> players = new EnumMap<>(PlayerId.class);
    private final Deque<Step> steps;
    private final Deque<QueuedTrigger> queue;
    private final List<QueuedTrigger> raised = new ArrayList<>();
    private final List<GameEvent> events = new ArrayList<>();
    private final SplitMix64 rng;
    private int turn;
    private PlayerId active;
    private Optional<Decision> pending;
    private Optional<GameResult> result;
    private int nextInstanceId;
    private int nextArrivalSeq;
    private int decisionSeq;

    private Game(GameState state, CardCatalog catalog) {
        this.catalog = catalog;
        this.firstPlayer = state.firstPlayer();
        players.put(PlayerId.P1, state.p1());
        players.put(PlayerId.P2, state.p2());
        this.steps = new ArrayDeque<>(state.resolution().steps());
        this.queue = new ArrayDeque<>(state.resolution().queue());
        this.rng = new SplitMix64(state.rng());
        this.turn = state.turn();
        this.active = state.active();
        this.pending = state.pending();
        this.result = state.result();
        this.nextInstanceId = state.nextInstanceId();
        this.nextArrivalSeq = state.nextArrivalSeq();
        this.decisionSeq = state.decisionSeq();
    }

    public static Game of(GameState state, CardCatalog catalog) {
        return new Game(state, catalog);
    }

    public GameState toState() {
        return new GameState(turn, active, firstPlayer, players.get(PlayerId.P1), players.get(PlayerId.P2),
                new Resolution(List.copyOf(steps), List.copyOf(queue)), pending, rng.state(), nextInstanceId,
                nextArrivalSeq, decisionSeq, result);
    }

    public CardCatalog catalog() {
        return catalog;
    }

    public SplitMix64 rng() {
        return rng;
    }

    // Turn and players

    public int turn() {
        return turn;
    }

    public PlayerId active() {
        return active;
    }

    public PlayerId firstPlayer() {
        return firstPlayer;
    }

    public void startTurnOf(PlayerId player) {
        turn++;
        active = player;
    }

    public PlayerState player(PlayerId id) {
        return players.get(id);
    }

    public void updatePlayer(PlayerId id, UnaryOperator<PlayerState> change) {
        players.put(id, change.apply(players.get(id)));
    }

    public Optional<Unit> unit(InstanceId id) {
        return player(PlayerId.P1).unit(id).or(() -> player(PlayerId.P2).unit(id));
    }

    public Optional<Relic> relic(InstanceId id) {
        return player(PlayerId.P1).relic(id).or(() -> player(PlayerId.P2).relic(id));
    }

    public void updateUnit(Unit changed) {
        updatePlayer(changed.controller(), player -> player.replaceUnit(changed));
    }

    /** Every unit on the board, oldest arrival first. */
    public List<Unit> unitsByArrival() {
        return players.values().stream()
                .flatMap(player -> player.units().stream())
                .sorted(Comparator.comparingInt(Unit::arrivalSeq))
                .toList();
    }

    public InstanceId newInstanceId() {
        return InstanceId.of(nextInstanceId++);
    }

    public int newArrivalSeq() {
        return nextArrivalSeq++;
    }

    // Resolution

    /** Puts steps at the front, the first one given running first. */
    public void push(Step... newSteps) {
        for (int i = newSteps.length - 1; i >= 0; i--) {
            steps.addFirst(newSteps[i]);
        }
    }

    public Optional<Step> peekStep() {
        return Optional.ofNullable(steps.peekFirst());
    }

    public Step popStep() {
        return steps.removeFirst();
    }

    /** An ability triggered during the current step; it joins the queue once the step is done (9.8). */
    public void raise(QueuedTrigger trigger) {
        raised.add(trigger);
    }

    public List<QueuedTrigger> takeRaised() {
        List<QueuedTrigger> taken = List.copyOf(raised);
        raised.clear();
        return taken;
    }

    public void enqueue(List<QueuedTrigger> triggers) {
        queue.addAll(triggers);
    }

    public boolean hasQueuedTriggers() {
        return !queue.isEmpty();
    }

    public QueuedTrigger pollTrigger() {
        return queue.removeFirst();
    }

    // Decisions and the end of the game

    public Optional<Decision> pending() {
        return pending;
    }

    /** Asks a decision that starts new work: MULLIGAN or MAIN. */
    public void ask(PlayerId player, DecisionKind kind, List<Action> actions) {
        if (kind.pausesAStep()) {
            throw new IllegalStateException(kind + " is asked in the middle of a step: use pauseAndAsk");
        }
        setPending(player, kind, actions);
    }

    /**
     * Puts {@code paused} back at the front of the pending work and asks: the answer will resume that step
     * ({@code StepRunner.resume}). It is how a step stops in the middle to let a player choose.
     */
    public void pauseAndAsk(Step paused, PlayerId player, DecisionKind kind, List<Action> actions) {
        if (!kind.pausesAStep()) {
            throw new IllegalStateException(kind + " does not pause a step: use ask");
        }
        push(paused);
        setPending(player, kind, actions);
    }

    private void setPending(PlayerId player, DecisionKind kind, List<Action> actions) {
        decisionSeq++;
        pending = Optional.of(new Decision("d-" + decisionSeq, player, kind, actions));
    }

    public void clearPending() {
        pending = Optional.empty();
    }

    public boolean isOver() {
        return result.isPresent();
    }

    /** Whatever has not resolved yet never resolves (1.6). */
    public void end(GameResult gameResult, List<String> rules) {
        result = Optional.of(gameResult);
        pending = Optional.empty();
        steps.clear();
        queue.clear();
        raised.clear();
        emit(new GameEvent.GameEnded(gameResult, rules));
    }

    // Events

    public void emit(GameEvent event) {
        events.add(event);
    }

    public List<GameEvent> events() {
        return List.copyOf(events);
    }
}
