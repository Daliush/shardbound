package fr.daliush.shardbound.api.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Seat tokens and join codes: 32 random bytes in base64url, stored as SHA-256 hashes, compared in constant time. */
public final class SeatTokens {

    private static final int BYTES = 32;

    private final SecureRandom random;

    public SeatTokens(SecureRandom random) {
        this.random = random;
    }

    public String generate() {
        byte[] bytes = new byte[BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        return HexFormat.of().formatHex(sha256(token));
    }

    public static boolean matches(String token, String hash) {
        return token != null && MessageDigest.isEqual(sha256(token), HexFormat.of().parseHex(hash));
    }

    private static byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform has SHA-256", e);
        }
    }
}
