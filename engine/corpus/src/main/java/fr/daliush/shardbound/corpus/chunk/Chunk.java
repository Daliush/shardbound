package fr.daliush.shardbound.corpus.chunk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * One piece of the Arbiter's knowledge: a rule, a vocabulary term or a card. Only {@code text} is embedded, indexed
 * and shown to the model. {@code name} is what a card or a term is called, by which the RAG finds the cards a question
 * names; a rule has none, since it is found by its text. {@code refs} are the chunks it relies on, and {@code hash}
 * tells the index when to embed it again.
 */
public record Chunk(String id, Kind kind, String name, String text, List<String> refs, String source, String hash) {

    public enum Kind {
        RULE, TERM, CARD
    }

    public Chunk {
        if ((kind == Kind.RULE) != (name == null)) {
            throw new IllegalArgumentException("A rule has no name, a term or a card has one: " + id);
        }
        refs = List.copyOf(refs);
    }

    /** A new rule's chunk, with no name. */
    public static Chunk rule(String id, String text, List<String> refs, String source) {
        return new Chunk(id, Kind.RULE, null, text, refs, source, sha256Hex(text));
    }

    /** A new vocabulary term's chunk, named after its term. */
    public static Chunk term(String id, String name, String text, List<String> refs, String source) {
        return new Chunk(id, Kind.TERM, name, text, refs, source, sha256Hex(text));
    }

    /** A new card's chunk, named after its card. */
    public static Chunk card(String id, String name, String text, List<String> refs, String source) {
        return new Chunk(id, Kind.CARD, name, text, refs, source, sha256Hex(text));
    }

    public Chunk copyWithRefs(List<String> newRefs) {
        return new Chunk(id, kind, name, text, newRefs, source, hash);
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
