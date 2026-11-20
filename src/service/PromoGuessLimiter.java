package service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Stops promo codes from being guessed: after MAX_WRONG wrong codes in a row a customer can't check
 * or use any promo code for LOCK. Works like the wrong-password lockout and, like it, is saved in a
 * small file (data/promo-attempts.properties) so restarting RentNGo doesn't reset it.
 */
class PromoGuessLimiter {
    static final int MAX_WRONG = 5;
    static final Duration LOCK = Duration.ofMinutes(15);

    private static final class Attempts {
        int wrong;
        Instant lockedUntil;
    }

    private final File file; // null = in memory only (tests)
    private final Map<String, Attempts> attempts = new HashMap<>();
    private Clock clock = Clock.systemUTC();

    PromoGuessLimiter(File file) {
        this.file = file;
        load();
    }

    /** For tests: a fixed or moving clock instead of the real one. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** "Too many wrong promo codes. Try again in X minutes." while locked, otherwise null. */
    String lockMessage(String customerId) {
        Attempts a = attempts.get(customerId);
        if (a == null || a.lockedUntil == null) return null;
        Instant now = clock.instant();
        if (!now.isBefore(a.lockedUntil)) {
            attempts.remove(customerId); // lock expired: start counting again
            save();
            return null;
        }
        long minutes = Math.max(1, (Duration.between(now, a.lockedUntil).getSeconds() + 59) / 60);
        return "Too many wrong promo codes. Try again in " + minutes + (minutes == 1 ? " minute." : " minutes.");
    }

    /** Counts a wrong code; returns true if this one locked the customer. */
    boolean recordWrong(String customerId) {
        Attempts a = attempts.computeIfAbsent(customerId, k -> new Attempts());
        a.wrong++;
        boolean locked = a.wrong >= MAX_WRONG;
        if (locked) {
            a.wrong = 0;
            a.lockedUntil = clock.instant().plus(LOCK);
        }
        save();
        return locked;
    }

    /** A code that worked starts the count again. */
    void recordSuccess(String customerId) {
        if (attempts.remove(customerId) != null) save();
    }

    private void load() {
        if (file == null || !file.isFile()) return;
        Properties props = new Properties();
        try (Reader in = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("Could not read " + file.getName() + ": " + e.getMessage());
            return;
        }
        for (String key : props.stringPropertyNames()) {
            try {
                String[] parts = props.getProperty(key).split(",", -1);
                Attempts a = new Attempts();
                a.wrong = Integer.parseInt(parts[0]);
                a.lockedUntil = parts.length > 1 && !parts[1].isEmpty() ? Instant.ofEpochMilli(Long.parseLong(parts[1])) : null;
                attempts.put(key, a);
            } catch (RuntimeException ignored) {
                // a damaged line just means no count for that customer
            }
        }
    }

    private void save() {
        if (file == null) return;
        Properties props = new Properties();
        attempts.forEach((key, a) -> props.setProperty(key,
                a.wrong + "," + (a.lockedUntil == null ? "" : String.valueOf(a.lockedUntil.toEpochMilli()))));
        try (Writer out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            props.store(out, "RentNGo wrong promo code counts (customerId=wrong,lockedUntilMillis)");
        } catch (IOException e) {
            System.err.println("Could not save " + file.getName() + ": " + e.getMessage());
        }
    }
}
