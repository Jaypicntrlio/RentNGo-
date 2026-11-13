package service;

import model.Customer;
import model.User;
import model.exceptions.AuthenticationException;
import model.exceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** JUnit 5 tests for login lockout, changing passwords, forced password changes and old-hash upgrades. */
class LoginSecurityTest {

    private static final String INVALID = "Invalid username or password";

    @TempDir
    Path dataDir;

    private MovableClock clock;
    private DataStore store;
    private AuthService auth;

    /** A clock the tests can move forward. */
    static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-29T09:00:00Z");

        void advance(Duration d) { now = now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    @BeforeEach
    void setUp() {
        clock = new MovableClock();
        restart();
    }

    /** Reopens the data folder (counters are in memory, so they start fresh, like the real app). */
    private void restart() {
        store = Stores.asAdmin(dataDir);
        auth = store.getAuthService();
        auth.setClock(clock);
    }

    private String loginError(String username, String password) {
        return assertThrows(AuthenticationException.class, () -> auth.login(username, password)).getMessage();
    }

    // ===== Lockout =====

    @Nested
    class Lockout {

        @Test
        void fiveWrongPasswordsLockTheUsernameForFiveMinutes() throws Exception {
            for (int i = 1; i <= 4; i++) assertEquals(INVALID, loginError("admin", "wrong" + i));
            assertEquals("Too many attempts. Try again in 5 minutes.", loginError("admin", "wrong5"));

            assertEquals("Too many attempts. Try again in 5 minutes.", loginError("admin", "admin123"),
                    "even the right password is refused while locked");
            clock.advance(Duration.ofMinutes(4).plusSeconds(1));
            assertEquals("Too many attempts. Try again in 1 minute.", loginError("admin", "admin123"));
            clock.advance(Duration.ofSeconds(59));
            assertNotNull(auth.login("admin", "admin123"), "unlocked after 5 minutes");
        }

        @Test
        void successfulLoginResetsTheCounter() throws Exception {
            for (int i = 0; i < 4; i++) loginError("admin", "wrong");
            auth.login("admin", "admin123");
            for (int i = 0; i < 4; i++) assertEquals(INVALID, loginError("admin", "wrong"));
            assertNotNull(auth.login("admin", "admin123"), "4 + 4 failures with a success between don't lock");
        }

        @Test
        void otherUsernamesAreNotAffected() throws Exception {
            for (int i = 0; i < 5; i++) loginError("admin", "wrong");
            assertNotNull(auth.login("john", "john123"));
        }

        @Test
        void usernameCaseDoesntGetAroundTheLimit() {
            for (String name : new String[]{"admin", "ADMIN", "Admin", " admin ", "aDmIn"}) loginError(name, "wrong");
            assertTrue(loginError("admin", "admin123").startsWith("Too many attempts"));
        }

        @Test
        void unknownUsernameGetsTheSameMessagesAsAWrongPassword() {
            assertEquals(loginError("admin", "wrong"), loginError("nobody", "wrong"), "same message, so accounts can't be discovered");
            for (int i = 0; i < 3; i++) loginError("nobody", "wrong");
            assertEquals("Too many attempts. Try again in 5 minutes.", loginError("nobody", "wrong"),
                    "unknown usernames lock the same way");
        }

        @Test
        void approverPasswordInRegisterAdminSharesTheLimit() throws Exception {
            for (int i = 1; i <= 4; i++) {
                ValidationException e = assertThrows(ValidationException.class, () ->
                        auth.registerAdmin("boss", "secret123", "Boss", "b@example.com", "", "admin", "wrong"));
                assertTrue(e.getMessage().contains("username or password is incorrect"), e.getMessage());
            }
            ValidationException locked = assertThrows(ValidationException.class, () ->
                    auth.registerAdmin("boss", "secret123", "Boss", "b@example.com", "", "admin", "wrong"));
            assertEquals("Too many attempts. Try again in 5 minutes.", locked.getMessage());
            assertThrows(ValidationException.class, () ->
                    auth.registerAdmin("boss", "secret123", "Boss", "b@example.com", "", "admin", "admin123"),
                    "the right approver password is refused while locked");
            assertTrue(loginError("admin", "admin123").startsWith("Too many attempts"), "logging in is locked too");

            clock.advance(Duration.ofMinutes(5));
            assertNotNull(auth.registerAdmin("boss", "secret123", "Boss", "b@example.com", "", "admin", "admin123"));
        }

        @Test
        void lockoutIsRecordedInTheAuditLog() {
            for (int i = 0; i < 5; i++) loginError("admin", "wrong");
            assertTrue(store.getBookingService().getAuditLog().entriesNewestFirst().stream()
                    .anyMatch(e -> e.getAction().equals(AuditLog.LOGIN_LOCKED) && e.getDetails().contains("'admin'")));
        }
    }

    // ===== changePassword =====

    @Nested
    class ChangePassword {

        @BeforeEach
        void logInAsJohn() throws Exception {
            auth.login("john", "john123"); // you can only change your own password
        }

        @Test
        void changesThePasswordAndKeepsItAfterRestart() throws Exception {
            auth.changePassword("CUS001", "john123", "newPass2026");
            assertEquals(INVALID, loginError("john", "john123"));
            assertNotNull(auth.login("john", "newPass2026"));
            restart();
            assertNotNull(auth.login("john", "newPass2026"), "saved to accounts.txt");
            assertTrue(store.getBookingService().getAuditLog().entriesNewestFirst().stream()
                    .anyMatch(e -> e.getAction().equals(AuditLog.PASSWORD_CHANGED) && e.getUserId().equals("CUS001")));
        }

        @Test
        void wrongCurrentPasswordIsRefusedAndCountsTowardsLockout() {
            ValidationException e = assertThrows(ValidationException.class,
                    () -> auth.changePassword("CUS001", "not-it", "newPass2026"));
            assertEquals("Current password is incorrect", e.getMessage());
            for (int i = 0; i < 3; i++) assertThrows(ValidationException.class, () -> auth.changePassword("CUS001", "not-it", "newPass2026"));
            ValidationException locked = assertThrows(ValidationException.class,
                    () -> auth.changePassword("CUS001", "not-it", "newPass2026"));
            assertTrue(locked.getMessage().startsWith("Too many attempts"), locked.getMessage());
            assertTrue(loginError("john", "john123").startsWith("Too many attempts"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"short1", "abcdefgh", "12345678", "        ", ""})
        void weakNewPasswordsAreRefused(String weak) throws Exception {
            ValidationException e = assertThrows(ValidationException.class, () -> auth.changePassword("CUS001", "john123", weak));
            assertEquals("Password must be at least 8 characters, with a letter and a number", e.getMessage());
            assertNotNull(auth.login("john", "john123"), "old password still works");
        }

        @Test
        void newPasswordMustDifferFromTheOldOne() throws Exception {
            auth.changePassword("CUS001", "john123", "firstPass1");
            ValidationException e = assertThrows(ValidationException.class,
                    () -> auth.changePassword("CUS001", "firstPass1", "firstPass1"));
            assertEquals("New password must be different from the current one", e.getMessage());
        }

        @Test
        void unknownAccountIsRefused() {
            assertThrows(ValidationException.class, () -> auth.changePassword("CUS999", "x", "newPass2026"));
        }
    }

    // ===== Forced change for accounts given the default password =====

    @Nested
    class ForcedChange {

        @Test
        void customerAddedByAdminMustChangePasswordAndItIsSaved() throws Exception {
            Customer added = auth.addCustomer("Pedro Cruz", "", "0917-000-0001", "N02-00-000001");
            assertTrue(added.isMustChangePassword());

            String startPassword = auth.takeTemporaryPassword(added.getUserId());
            restart();
            User user = auth.login(added.getUsername(), startPassword);
            assertTrue(user.isMustChangePassword(), "the flag is saved in accounts.txt");

            auth.changePassword(user.getUserId(), startPassword, "pedroPass77");
            assertFalse(auth.getCurrentUser().isMustChangePassword(), "changing the password clears it");
            restart();
            assertFalse(auth.login(added.getUsername(), "pedroPass77").isMustChangePassword());
        }

        @Test
        void selfSignUpAndOldFilesDontRequireAChange() throws Exception {
            Customer signedUp = auth.registerCustomer("maria", "mariaPass1", "Maria Santos", "m@example.com", "", "N01-23-456789");
            assertFalse(signedUp.isMustChangePassword());

            // accounts.txt from before the column existed: drop the last column from every row
            Path file = dataDir.resolve("accounts.txt");
            List<String> lines = new ArrayList<>();
            for (String line : Files.readAllLines(file)) {
                lines.add(line.startsWith("#") ? line : line.substring(0, line.lastIndexOf('\t')));
            }
            Files.write(file, lines);
            restart();
            assertNull(auth.getLoadError());
            assertTrue(auth.getAllUsers().stream().noneMatch(User::isMustChangePassword));
        }
    }

    // ===== Password hashes =====

    @Nested
    class Hashes {

        /** Puts an old-format "salt$hash" (10,000 rounds) in accounts.txt for john. */
        private String giveJohnALegacyHash() throws Exception {
            Path file = dataDir.resolve("accounts.txt");
            String legacy = PasswordHasher.legacyHash("john123");
            List<String> lines = new ArrayList<>();
            for (String line : Files.readAllLines(file)) {
                String[] f = line.split("\t", -1);
                if (f.length > 3 && f[2].equals("john")) {
                    f[3] = legacy;
                    line = String.join("\t", f);
                }
                lines.add(line);
            }
            Files.write(file, lines);
            restart();
            return legacy;
        }

        /** The hash saved for john in accounts.txt (hashes are never handed out by the services). */
        private String johnsStoredHash() throws Exception {
            for (String line : Files.readAllLines(dataDir.resolve("accounts.txt"))) {
                String[] f = line.split("	", -1);
                if (f.length > 3 && f[2].equals("john")) return f[3];
            }
            throw new AssertionError("john not in accounts.txt");
        }

        @Test
        void newHashesStoreTheRoundCount() {
            String hash = PasswordHasher.hash("secret123");
            assertTrue(hash.startsWith("100000$"), hash);
            assertEquals(3, hash.split("\\$").length);
            assertTrue(PasswordHasher.matches(hash, "secret123"));
            assertFalse(PasswordHasher.matches(hash, "secret124"));
            assertFalse(PasswordHasher.needsRehash(hash));
        }

        @Test
        void oldHashStillWorksAndIsUpgradedOnLogin() throws Exception {
            String legacy = giveJohnALegacyHash();
            assertTrue(PasswordHasher.needsRehash(legacy));

            assertEquals(INVALID, loginError("john", "wrong-pass"));
            assertEquals(legacy, johnsStoredHash(), "a failed login doesn't touch the hash");

            assertNotNull(auth.login("john", "john123"), "the old 10,000-round hash still verifies");
            String upgraded = johnsStoredHash();
            assertTrue(upgraded.startsWith(PasswordHasher.ROUNDS + "$"), upgraded);
            String saved = Files.readString(dataDir.resolve("accounts.txt"));
            assertTrue(saved.contains(upgraded) && !saved.contains(legacy), "the new hash replaces the old one on disk");

            restart();
            assertNotNull(auth.login("john", "john123"), "and still works after a restart");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "$", "$$", "abc", "x$y$z", "100000$!!$??", "999999999$AAAA$AAAA",
                "100000$AAAA$AAAA", "-5$AAAA$AAAA", "100000$$", "a$b$c$d"})
        void malformedHashesNeverMatchAndNeverThrow(String stored) {
            assertFalse(assertDoesNotThrow(() -> PasswordHasher.matches(stored, "x")));
            assertTrue(assertDoesNotThrow(() -> PasswordHasher.needsRehash(stored)));
        }

        @Test
        void nullsNeverMatch() {
            assertFalse(PasswordHasher.matches(null, "x"));
            assertFalse(PasswordHasher.matches(PasswordHasher.hash("secret123"), null));
        }
    }
}
