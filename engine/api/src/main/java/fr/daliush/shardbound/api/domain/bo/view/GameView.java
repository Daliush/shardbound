package fr.daliush.shardbound.api.domain.bo.view;

import java.util.Optional;

/** Everything one seat may see of a game, at one version (spec §13.3). */
public record GameView(
        String gameId,
        int version,
        String status,
        int turn,
        Optional<String> activePlayer,
        boolean yourTurn,
        SelfView you,
        Optional<OpponentView> opponent,
        Optional<DecisionView> decision,
        Optional<WaitingForView> waitingFor,
        Optional<ResultView> result) {

    public static final String WAITING_FOR_OPPONENT = "waiting_for_opponent";
    public static final String IN_PROGRESS = "in_progress";
    public static final String FINISHED = "finished";

    /** The opponent must decide: who, and which kind of decision, without their options. */
    public record WaitingForView(String player, String kind) {
    }

    /** {@code outcome} is "win", "loss" or "draw", seen by the viewer. */
    public record ResultView(String outcome, String reason) {
    }
}
