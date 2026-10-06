package fr.daliush.shardbound.api.session;

import java.util.Locale;

/** Why a message was not applied (spec §13.2). */
public record Rejection(Reason reason, String message) {

    public enum Reason {
        NOT_YOUR_DECISION, STALE_DECISION, INVALID_ACTION, MALFORMED_MESSAGE, GAME_NOT_STARTED, GAME_OVER;

        /** "stale_decision". */
        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
