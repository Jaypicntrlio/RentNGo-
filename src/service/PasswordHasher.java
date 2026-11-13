package service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Salted, iterated SHA-256 password hashes, so the accounts file never contains a readable password.
 *
 * New hashes are stored as "rounds$salt$hash" (salt and hash in Base64), e.g. "100000$...$...".
 * Older hashes stored as "salt$hash" were made with 10,000 rounds; they still verify, and
 * {@link #needsRehash} tells the caller to replace them after the next successful login.
 */
public final class PasswordHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Rounds used for new hashes. */
    public static final int ROUNDS = 100_000;
    /** Rounds behind the old two-part "salt$hash" format. */
    static final int LEGACY_ROUNDS = 10_000;
    /** Upper bound accepted from a stored value, so a damaged file can't make a check run forever. */
    private static final int MAX_ROUNDS = 10_000_000;

    private PasswordHasher() {}

    public static String hash(String password) {
        return hash(password, ROUNDS);
    }

    /** Package-private so tests can make old-format or cheaper hashes. */
    static String hash(String password, int rounds) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return rounds + "$" + encode(salt) + "$" + encode(digest(salt, password, rounds));
    }

    /** An old-format "salt$hash" value (10,000 rounds), as written before the round count was stored. */
    static String legacyHash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encode(salt) + "$" + encode(digest(salt, password, LEGACY_ROUNDS));
    }

    /** True if the password matches the stored hash. Never throws: a malformed value simply doesn't match. */
    public static boolean matches(String stored, String password) {
        if (stored == null || password == null) return false;
        try {
            Parsed p = parse(stored);
            if (p == null) return false;
            return MessageDigest.isEqual(p.hash, digest(p.salt, password, p.rounds));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True for hashes in the old format or with fewer rounds than {@link #ROUNDS}; re-hash them after login. */
    public static boolean needsRehash(String stored) {
        try {
            Parsed p = parse(stored);
            return p == null || p.rounds < ROUNDS;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static final class Parsed {
        final int rounds;
        final byte[] salt;
        final byte[] hash;

        Parsed(int rounds, byte[] salt, byte[] hash) {
            this.rounds = rounds;
            this.salt = salt;
            this.hash = hash;
        }
    }

    /** Splits a stored value, or returns null if it isn't one of the two known formats. */
    private static Parsed parse(String stored) {
        if (stored == null) return null;
        String[] parts = stored.split("\\$", -1);
        int rounds;
        String salt, hash;
        if (parts.length == 2) {
            rounds = LEGACY_ROUNDS;
            salt = parts[0];
            hash = parts[1];
        } else if (parts.length == 3 && parts[0].matches("\\d{1,8}")) {
            rounds = Integer.parseInt(parts[0]);
            salt = parts[1];
            hash = parts[2];
        } else {
            return null;
        }
        if (rounds < 1 || rounds > MAX_ROUNDS || salt.isEmpty() || hash.isEmpty()) return null;
        byte[] hashBytes = Base64.getDecoder().decode(hash);
        if (hashBytes.length != 32) return null; // SHA-256 output
        return new Parsed(rounds, Base64.getDecoder().decode(salt), hashBytes);
    }

    private static byte[] digest(byte[] salt, String password, int rounds) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] out = md.digest(password.getBytes(StandardCharsets.UTF_8));
            for (int i = 1; i < rounds; i++) {
                md.reset();
                out = md.digest(out);
            }
            return out;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
