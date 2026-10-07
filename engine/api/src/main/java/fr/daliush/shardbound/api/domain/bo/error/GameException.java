package fr.daliush.shardbound.api.domain.bo.error;

/** A request the game services refuse, with the reason a client can act on. */
public abstract sealed class GameException extends RuntimeException {

    private GameException(String message) {
        super(message);
    }

    /** No such game, or it was evicted. */
    public static final class UnknownGame extends GameException {
        public UnknownGame(String id) {
            super("Unknown game: " + id);
        }
    }

    /** An unknown deck or bot, or an illegal deck. */
    public static final class InvalidRequest extends GameException {
        public InvalidRequest(String message) {
            super(message);
        }
    }

    /** A wrong join code, or a game that is already full. */
    public static final class JoinRefused extends GameException {
        public JoinRefused() {
            super("Wrong join code, or the game is already full.");
        }
    }

    /** A token that holds no seat of the game. */
    public static final class InvalidToken extends GameException {
        public InvalidToken() {
            super("This token holds no seat in this game.");
        }
    }
}
