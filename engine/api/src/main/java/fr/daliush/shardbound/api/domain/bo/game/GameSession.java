package fr.daliush.shardbound.api.domain.bo.game;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.rules.Transition;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Everything about one hosted game, loaded and saved around every message so any instance can process the
 * next one. {@code state} is a snapshot for fast loading; the seed, the seats' decks and {@code actions} rebuild
 * it exactly. {@code events} is the unredacted log. {@code version} grows by 1 at every save.
 */
public record GameSession(
        GameId id,
        GameStatus status,
        long seed,
        Seat p1,
        Seat p2,
        Optional<GameState> state,
        int version,
        List<GameEvent> events,
        List<Action> actions,
        Outbox outbox,
        Instant lastActivity) {

    public GameSession {
        events = List.copyOf(events);
        actions = List.copyOf(actions);
    }

    /** A game against a human, before they join: there is no game state yet. */
    public static GameSession waiting(GameId id, long seed, Seat.Human creator, Seat.Open opponent, Instant now) {
        return new GameSession(id, GameStatus.WAITING_FOR_OPPONENT, seed, creator, opponent, Optional.empty(), 0,
                List.of(), List.of(), Outbox.empty(), now);
    }

    /** A game against a bot, set up at creation. */
    public static GameSession started(GameId id, long seed, Seat p1, Seat p2, Transition setup, Instant now) {
        return new GameSession(id, GameStatus.IN_PROGRESS, seed, p1, p2, Optional.of(setup.state()), 0,
                setup.events(), List.of(), Outbox.empty(), now);
    }

    public Seat seat(PlayerId player) {
        return player == PlayerId.P1 ? p1 : p2;
    }

    /** The join sets the game up, and is saved like any other change (spec §13.2). */
    public GameSession joined(Seat.Human joiner, Transition setup, Instant now) {
        return new GameSession(id, GameStatus.IN_PROGRESS, seed, p1, joiner, Optional.of(setup.state()), version + 1,
                setup.events(), actions, outbox, now);
    }

    public GameSession applied(Action action, Transition transition, Instant now) {
        GameStatus next = transition.state().isOver() ? GameStatus.FINISHED : GameStatus.IN_PROGRESS;
        return new GameSession(id, next, seed, p1, p2, Optional.of(transition.state()), version + 1,
                append(events, transition.events()), append(actions, List.of(action)), outbox, now);
    }

    public GameSession withSeat(Seat seat) {
        Seat newP1 = seat.player() == PlayerId.P1 ? seat : p1;
        Seat newP2 = seat.player() == PlayerId.P2 ? seat : p2;
        return new GameSession(id, status, seed, newP1, newP2, state, version, events, actions, outbox,
                lastActivity);
    }

    public GameSession withOutbox(Outbox newOutbox) {
        return new GameSession(id, status, seed, p1, p2, state, version, events, actions, newOutbox, lastActivity);
    }

    private static <T> List<T> append(List<T> list, List<? extends T> more) {
        List<T> copy = new ArrayList<>(list);
        copy.addAll(more);
        return copy;
    }
}
