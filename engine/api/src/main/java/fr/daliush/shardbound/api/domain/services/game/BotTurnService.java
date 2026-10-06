package fr.daliush.shardbound.api.domain.services.game;

import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.game.GameSession;
import fr.daliush.shardbound.api.domain.bo.game.GameStatus;
import fr.daliush.shardbound.api.domain.bo.game.Seat;
import fr.daliush.shardbound.api.domain.ports.GameSessionPort;
import fr.daliush.shardbound.api.domain.services.bot.BotRoster;
import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.bot.Bot;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.state.GameState;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * While a bot holds the decision, it plays: one save and one update per move. Bots are rebuilt at every move
 * from their seat, so whichever instance loads a game left on a bot's decision can resume it (spec §13.4).
 */
@Service
public class BotTurnService {

    /** Guards against a bot loop that never gives the decision back. */
    static final int MAX_BOT_MOVES = 10_000;

    private final GameEngine engine;
    private final GameSessionPort sessions;
    private final GameSaver saver;
    private final BotRoster roster;
    private final GameLocks locks;
    private final GameSettings settings;

    public BotTurnService(GameEngine engine, GameSessionPort sessions, GameSaver saver, BotRoster roster,
                          GameLocks locks, GameSettings settings) {
        this.engine = engine;
        this.sessions = sessions;
        this.saver = saver;
        this.roster = roster;
        this.locks = locks;
        this.settings = settings;
    }

    /** Plays every move the bots owe in this game; does nothing when the decision is a human's. */
    public void resume(GameId id) {
        locks.run(id, () -> play(id));
    }

    private void play(GameId id) {
        for (int moves = 0; ; moves++) {
            GameSession session = sessions.load(id);
            Optional<Seat.Bot> seat = botToPlay(session);
            if (seat.isEmpty()) {
                return;
            }
            if (moves == MAX_BOT_MOVES) {
                throw new IllegalStateException("Bots made " + MAX_BOT_MOVES + " moves in a row in game " + id);
            }
            pauseBetweenMoves(moves);
            GameState state = session.state().orElseThrow();
            Decision decision = engine.decision(state).orElseThrow();
            Bot bot = roster.rebuild(seat.get());
            Action action = bot.choose(engine.view(state, decision.player(), session.events()), decision);
            saver.apply(session.withSeat(seat.get().withRngState(bot.rngState())), action);
        }
    }

    private Optional<Seat.Bot> botToPlay(GameSession session) {
        if (session.status() != GameStatus.IN_PROGRESS) {
            return Optional.empty();
        }
        return engine.decision(session.state().orElseThrow())
                .map(decision -> session.seat(decision.player()))
                .filter(Seat.Bot.class::isInstance)
                .map(Seat.Bot.class::cast);
    }

    private void pauseBetweenMoves(int moves) {
        if (moves > 0 && settings.botStepDelay().isPositive()) {
            try {
                Thread.sleep(settings.botStepDelay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted between two bot moves", e);
            }
        }
    }
}
