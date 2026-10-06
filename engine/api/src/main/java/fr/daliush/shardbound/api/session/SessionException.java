package fr.daliush.shardbound.api.session;

/** A request the session server refuses, with the reason a client can act on. */
public sealed class SessionException extends RuntimeException {

    private SessionException(String message) {
        super(message);
    }

    /** No such game, or it was evicted. */
    public static final class UnknownGame extends SessionException {
        public UnknownGame(String id) {
            super("Unknown game: " + id);
        }
    }

    /** An unknown deck or bot, or an illegal deck. */
    public static final class InvalidRequest extends SessionException {
        public InvalidRequest(String message) {
            super(message);
        }
    }

    /** A wrong join code, or a game that is already full. */
    public static final class JoinRefused extends SessionException {
        public JoinRefused() {
            super("Wrong join code, or the game is already full.");
        }
    }

    /** A token that holds no seat of the game. */
    public static final class InvalidToken extends SessionException {
        public InvalidToken() {
            super("This token holds no seat in this game.");
        }
    }
}
