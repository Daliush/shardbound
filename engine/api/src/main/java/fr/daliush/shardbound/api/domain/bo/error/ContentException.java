package fr.daliush.shardbound.api.domain.bo.error;

/** A request for game content that the content services refuse. */
public abstract sealed class ContentException extends RuntimeException {

    private ContentException(String message) {
        super(message);
    }

    /** The store holds no art for this card. */
    public static final class UnknownArt extends ContentException {
        public UnknownArt(String cardId) {
            super("No art for card: " + cardId);
        }
    }
}
