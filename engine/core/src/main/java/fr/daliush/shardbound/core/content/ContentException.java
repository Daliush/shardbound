package fr.daliush.shardbound.core.content;

/** The content files are invalid, or something asks for content that does not exist. */
public class ContentException extends RuntimeException {

    public ContentException(String message) {
        super(message);
    }

    public ContentException(String message, Throwable cause) {
        super(message, cause);
    }
}
