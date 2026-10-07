package fr.daliush.shardbound.api.domain.bo.command;

/** Why an answer was not applied (spec §13.2). */
public record Rejection(Reason reason, String message) {

    public enum Reason {
        NOT_YOUR_DECISION, STALE_DECISION, INVALID_ACTION, MALFORMED_MESSAGE, GAME_NOT_STARTED, GAME_OVER
    }
}
