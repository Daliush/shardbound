package fr.daliush.shardbound.corpus.chunk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * One piece of the Arbiter's knowledge: a rule, a vocabulary term or a card. Only {@code text} is embedded, indexed
 * and shown to the model; {@code refs} are the chunks it relies on, and {@code hash} tells the index when to embed it
 * again.
 */
public record Chunk(String id, Kind kind, String text, List<String> refs, String source, String hash) {

    public enum Kind {
        RULE, TERM, CARD
    }

    public Chunk {
        refs = List.copyOf(refs);
    }

    /** A new chunk, its hash computed from its text. */
    public Chunk(String id, Kind kind, String text, List<String> refs, String source) {
        this(id, kind, text, refs, source, sha256Hex(text));
    }

    public Chunk copyWithRefs(List<String> newRefs) {
        return new Chunk(id, kind, text, newRefs, source, hash);
    }

    private static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
