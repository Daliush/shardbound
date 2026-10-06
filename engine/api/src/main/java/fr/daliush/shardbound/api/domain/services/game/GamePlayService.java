package fr.daliush.shardbound.api.domain.services.game;

import fr.daliush.shardbound.api.domain.bo.command.Rejection.Reason;
import fr.daliush.shardbound.api.domain.bo.command.Rejection;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.game.GameSession;
import fr.daliush.shardbound.api.domain.bo.game.GameStatus;
import fr.daliush.shardbound.api.domain.ports.GameSessionPort;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * A seat answers its decision. The decision is recomputed from the saved state, so the client is never
 * trusted; a conflict means another instance moved the game first, and the answer is checked again.
 */
@Service
public class GamePlayService {

    private final GameEngine engine;
    private final GameSessionPort sessions;
    private final GameSaver saver;
    private final GameLocks locks;
    private final BotTurnService botTurns;

    public GamePlayService(GameEngine engine, GameSessionPort sessions, GameSaver saver, GameLocks locks,
                           BotTurnService botTurns) {
        this.engine = engine;
        this.sessions = sessions;
        this.saver = saver;
        this.locks = locks;
        this.botTurns = botTurns;
    }

    /** Applies the seat's answer, then lets the bots play. Empty when the answer was applied. */
    public Optional<Rejection> act(GameId id, PlayerId seat, String decisionId, int actionIndex) {
        return locks.call(id, () -> {
            while (true) {
                GameSession session = sessions.load(id);
                Optional<Rejection> rejection = check(session, seat, decisionId, actionIndex);
                if (rejection.isPresent()) {
                    return rejection;
                }
                Decision decision = engine.decision(session.state().orElseThrow()).orElseThrow();
                if (saver.apply(session, decision.actions().get(actionIndex))) {
                    botTurns.resume(id);
                    return Optional.empty();
                }
            }
        });
    }

    /** Spec §13.4, step 3: an old decision id is stale whoever holds the new decision. */
    private Optional<Rejection> check(GameSession session, PlayerId seat, String decisionId, int actionIndex) {
        if (session.status() == GameStatus.WAITING_FOR_OPPONENT) {
            return reject(Reason.GAME_NOT_STARTED, "The game starts when your opponent joins.");
        }
        Optional<Decision> pending = engine.decision(session.state().orElseThrow());
        if (pending.isEmpty()) {
            return reject(Reason.GAME_OVER, "The game is over.");
        }
        Decision decision = pending.get();
        if (!decision.id().equals(decisionId)) {
            return reject(Reason.STALE_DECISION, "Decision " + decisionId + " is no longer pending.");
        }
        if (decision.player() != seat) {
            return reject(Reason.NOT_YOUR_DECISION, "Decision " + decisionId + " is your opponent's.");
        }
        if (actionIndex < 0 || actionIndex >= decision.actions().size()) {
            return reject(Reason.INVALID_ACTION, "Decision " + decisionId + " has no action " + actionIndex + ".");
        }
        return Optional.empty();
    }

    private static Optional<Rejection> reject(Reason reason, String message) {
        return Optional.of(new Rejection(reason, message));
    }
}
