package service;

import model.*;
import model.exceptions.*;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class AuthService {
    /** Length of the random starting password for customers the admin adds from Manage Customers. */
    public static final int TEMPORARY_PASSWORD_LENGTH = 10;
    /** No 0/O, 1/l/I: the admin reads the password out or writes it down for the customer. */
    private static final String TEMPORARY_LETTERS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ";
    private static final String TEMPORARY_DIGITS = "23456789";
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    public static final String DEACTIVATED_MESSAGE = "This account has been deactivated. Please contact RentNGo.";
    /** Same message for an unknown username and a wrong password, so it doesn't reveal which accounts exist. */
    public static final String INVALID_LOGIN_MESSAGE = "Invalid username or password";
    /** Rule for new passwords (shown in the sign-up and change-password screens). */
    public static final String PASSWORD_RULE = "at least 8 characters, with a letter and a number";
    public static final String NOT_LOGGED_IN = "Please log in first.";
    public static final String CHANGE_PASSWORD_FIRST = "Please choose a new password first.";

    /** Wrong passwords allowed for one username before it is locked. */
    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCKOUT = Duration.ofMinutes(5);
    /** Checked against when the username doesn't exist, so that answer takes as long as a wrong password. */
    private static final String DUMMY_HASH = PasswordHasher.hash("not-a-real-password-1");

    /** Failed password attempts per username (lower case), kept in memory only. */
    private final Map<String, Attempts> attempts = new HashMap<>();
    private Clock clock = Clock.systemUTC();

    /** Starting passwords of customers just added, by user ID, until the admin's window has read them once. */
    private final Map<String, String> temporaryPasswords = new HashMap<>();

    private static final class Attempts {
        int failures;
        Instant lockedUntil;
    }

    private List<User> users;
    private User currentUser;
    private int customerSeq;
    private int adminSeq;
    private final AccountStore store;
    private String loadError;
    private boolean readOnly;
    private AuditLog audit = AuditLog.NONE;
    private boolean changedOnDisk;

    public AuthService() {
        this(new AccountStore());
    }

    /** Uses the given account file (tests pass a temporary one). */
    public AuthService(AccountStore store) {
        this.store = store;
        this.users = new ArrayList<>();
        loadUsers();
    }

    /**
     * Demo accounts are created only on the very first run (no accounts file yet). If the file exists
     * but can't be read, or no admin account could be loaded from it, the service goes read-only:
     * nothing is created and the file is never saved over, so real accounts can't be lost.
     */
    private void loadUsers() {
        if (store == null || !store.exists()) {
            initializeDefaultUsers();
            customerSeq = highestNumber("CUS");
            adminSeq = highestNumber("ADM");
            try {
                persist();
            } catch (ValidationException e) {
                System.err.println(e.getMessage());
            }
            return;
        }

        try {
            users.addAll(store.load());
        } catch (IOException e) {
            readOnly = true;
            loadError = TsvFile.describeUnreadable(store.getFile(), e);
            return;
        }
        loadError = store.getWarnings().summary();

        if (users.stream().noneMatch(u -> u instanceof Admin)) {
            readOnly = true;
            String noAdmin = store.getFile().getName() + " has no admin account that could be loaded, so it was left "
                    + "unchanged and no demo accounts were created. Fix or restore the file, then restart RentNGo.";
            loadError = loadError == null ? noAdmin : loadError + "\n" + noAdmin;
        }
        // the saved counters win, so IDs of deleted accounts are never handed out again
        customerSeq = Math.max(highestNumber("CUS"), store.getSequence("CUS"));
        adminSeq = Math.max(highestNumber("ADM"), store.getSequence("ADM"));
    }

    /** Where account changes are recorded (data/audit.txt). */
    public void setAuditLog(AuditLog audit) {
        this.audit = audit != null ? audit : AuditLog.NONE;
    }

    private static String describe(Customer c) {
        return c.getUserId() + " " + c.getFullName() + " (username " + c.getUsername() + ", license "
                + c.getLicenseNumber() + ", phone " + c.getPhone() + ", email " + c.getEmail() + ")";
    }

    /** Why the saved file couldn't be (fully) loaded, or null if it loaded fine (shown at startup). */
    public String getLoadError() {
        return loadError;
    }

    /**
     * True when accounts.txt couldn't be read or had no admin account. Nothing is saved in this
     * state, and the app refuses to start.
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    private void initializeDefaultUsers() {
        users.add(new Admin("ADM001", "admin", PasswordHasher.hash("admin123"), "System Administrator", "admin@rentngo.com", "555-0001", "SUPER_ADMIN"));
        users.add(new Customer("CUS001", "john", PasswordHasher.hash("john123"), "John Doe", "john@email.com", "555-1111", "DL123456"));
        users.add(new Customer("CUS002", "jane", PasswordHasher.hash("jane123"), "Jane Smith", "jane@email.com", "555-2222", "DL789012"));
    }

    private int highestNumber(String prefix) {
        int max = 0;
        for (User u : users) {
            String id = u.getUserId();
            if (id != null && id.startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(id.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // non-numeric id, skip
                }
            }
        }
        return max;
    }

    private String nextCustomerId() {
        customerSeq++;
        return "CUS" + String.format("%03d", customerSeq);
    }

    private String nextAdminId() {
        adminSeq++;
        return "ADM" + String.format("%03d", adminSeq);
    }

    /** Refuses changes while the accounts file could not be loaded, so it is never saved over. */
    private void requireWritable() throws ValidationException {
        if (readOnly) {
            throw new ValidationException(changedOnDisk ? TsvFile.CHANGED_ON_DISK
                    : "Accounts can't be saved because " + store.getFile().getName()
                    + " could not be loaded. Fix or restore the file, then restart RentNGo.");
        }
    }

    /** Writes every account to the accounts file. */
    private void persist() throws ValidationException {
        if (store == null) return;
        requireWritable();
        try {
            java.util.Map<String, Integer> seqs = new java.util.LinkedHashMap<>();
            seqs.put("CUS", customerSeq);
            seqs.put("ADM", adminSeq);
            store.save(users, seqs);
        } catch (TsvFile.DataChangedException e) {
            // someone else wrote accounts.txt: never overwrite it, and stop saving for this session
            readOnly = true;
            changedOnDisk = true;
            throw new ValidationException(e.getMessage());
        } catch (IOException e) {
            throw new ValidationException("Could not save accounts: " + e.getMessage());
        }
    }

    public User login(String username, String password) throws AuthenticationException {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            throw new AuthenticationException("Username and password are required");
        }

        String key = lockKey(username);
        String locked = lockMessage(key);
        if (locked != null) {
            throw new AuthenticationException(locked);
        }

        User user = findByUsername(username);
        if (!passwordMatches(user, password)) {
            String nowLocked = recordFailure(key);
            throw new AuthenticationException(nowLocked != null ? nowLocked : INVALID_LOGIN_MESSAGE);
        }
        attempts.remove(key);

        // checked only after the password matches, so it doesn't reveal which accounts exist
        if (!user.isActive()) {
            throw new AuthenticationException(DEACTIVATED_MESSAGE);
        }
        upgradeHashIfNeeded(user, password);
        currentUser = user;
        return publicCopy(user);
    }

    // ===== Lockout after too many wrong passwords =====

    /** For tests: a fixed or moving clock instead of the real one. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    private static String lockKey(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    /** "Too many attempts. Try again in X minutes." while the username is locked, otherwise null. */
    private String lockMessage(String key) {
        Attempts a = attempts.get(key);
        if (a == null || a.lockedUntil == null) return null;
        Instant now = clock.instant();
        if (!now.isBefore(a.lockedUntil)) {
            attempts.remove(key); // lock expired: start counting again
            return null;
        }
        long minutes = Math.max(1, (Duration.between(now, a.lockedUntil).getSeconds() + 59) / 60);
        return "Too many attempts. Try again in " + minutes + (minutes == 1 ? " minute." : " minutes.");
    }

    /** Counts a wrong password; returns the lock message if this attempt locked the username. */
    private String recordFailure(String key) {
        Attempts a = attempts.computeIfAbsent(key, k -> new Attempts());
        a.failures++;
        if (a.failures < MAX_FAILED_ATTEMPTS) return null;
        a.failures = 0;
        a.lockedUntil = clock.instant().plus(LOCKOUT);
        audit.recordAs("", AuditLog.LOGIN_LOCKED, null, "username '" + key + "' locked for " + LOCKOUT.toMinutes()
                + " minutes after " + MAX_FAILED_ATTEMPTS + " wrong passwords");
        return lockMessage(key);
    }

    private User findByUsername(String username) {
        if (username == null) return null;
        return users.stream().filter(u -> u.getUsername().equalsIgnoreCase(username.trim())).findFirst().orElse(null);
    }

    /** Hashes even for an unknown user, so both cases take about as long. */
    private static boolean passwordMatches(User user, String password) {
        if (user == null) {
            PasswordHasher.matches(DUMMY_HASH, password);
            return false;
        }
        return PasswordHasher.matches(user.getPassword(), password);
    }

    /** Re-saves an old-format or weaker hash with the current settings, now that the password is known. */
    private void upgradeHashIfNeeded(User user, String password) {
        if (store == null || readOnly || !PasswordHasher.needsRehash(user.getPassword())) return;
        String old = user.getPassword();
        user.setPassword(PasswordHasher.hash(password));
        try {
            persist();
        } catch (ValidationException e) {
            user.setPassword(old); // logging in still works; it is upgraded on a later login
        }
    }

    // ===== Changing passwords =====

    /** Throws unless the password is at least 8 characters with a letter and a number. */
    public static void validatePasswordStrength(String password) throws ValidationException {
        if (password == null || password.length() < 8
                || password.chars().noneMatch(Character::isLetter)
                || password.chars().noneMatch(Character::isDigit)) {
            throw new ValidationException("Password must be " + PASSWORD_RULE);
        }
    }

    /**
     * Changes a user's password. The current password must be right (wrong guesses count towards the
     * lockout), and the new one must meet the password rule and differ from the current one.
     * Also clears the "must change password" flag.
     */
    public void changePassword(String userId, String currentPassword, String newPassword) throws ValidationException {
        // allowed while a password change is required (that's the point), but only for your own account
        if (currentUser == null) {
            throw new ValidationException(NOT_LOGGED_IN);
        }
        if (!currentUser.getUserId().equals(userId)) {
            throw new ValidationException("You can only change your own password.");
        }
        requireWritable();
        User user = users.stream().filter(u -> u.getUserId().equals(userId)).findFirst()
                .orElseThrow(() -> new ValidationException("Account not found: " + userId));
        String key = lockKey(user.getUsername());
        String locked = lockMessage(key);
        if (locked != null) {
            throw new ValidationException(locked);
        }
        if (!PasswordHasher.matches(user.getPassword(), currentPassword)) {
            String nowLocked = recordFailure(key);
            throw new ValidationException(nowLocked != null ? nowLocked : "Current password is incorrect");
        }
        attempts.remove(key);
        validatePasswordStrength(newPassword);
        if (newPassword.equals(currentPassword)) {
            throw new ValidationException("New password must be different from the current one");
        }

        String oldHash = user.getPassword();
        boolean wasForced = user.isMustChangePassword();
        user.setPassword(PasswordHasher.hash(newPassword));
        user.setMustChangePassword(false);
        try {
            persist();
        } catch (ValidationException e) {
            user.setPassword(oldHash);
            user.setMustChangePassword(wasForced);
            throw e;
        }
        audit.recordAs(userId, AuditLog.PASSWORD_CHANGED, null,
                wasForced ? "password changed (required at first login)" : "password changed");
    }

    public void logout() {
        currentUser = null;
    }

    /** A copy of the logged-in user (without the password hash), or null. */
    public User getCurrentUser() {
        return currentUser == null ? null : publicCopy(currentUser);
    }

    // ===== Who may do what (used by every service) =====

    private enum Role { NONE, CUSTOMER, ADMIN }

    /** The logged-in user's role; someone who still has to change their password has none yet. */
    private Role role() {
        if (currentUser == null || !currentUser.isActive() || currentUser.isMustChangePassword()) return Role.NONE;
        return currentUser instanceof Admin ? Role.ADMIN : Role.CUSTOMER;
    }

    private String notAllowedYet() {
        return currentUser != null && currentUser.isMustChangePassword() ? CHANGE_PASSWORD_FIRST : NOT_LOGGED_IN;
    }

    /** Null when an admin is logged in; otherwise the reason the action is refused. */
    String adminOnly(String action) {
        switch (role()) {
            case ADMIN: return null;
            case CUSTOMER: return "Only an admin can " + action + ".";
            default: return notAllowedYet();
        }
    }

    /** Null when an admin, or this customer themselves, is logged in; otherwise the reason it is refused. */
    String adminOrSelf(String customerId, String action) {
        switch (role()) {
            case ADMIN: return null;
            case CUSTOMER:
                return currentUser.getUserId().equals(customerId) ? null : "You can only " + action + " for your own account.";
            default: return notAllowedYet();
        }
    }

    /** Whether the logged-in user may see this customer's data: admins see everyone, customers themselves. */
    boolean canSee(String customerId) {
        Role r = role();
        return r == Role.ADMIN || (r == Role.CUSTOMER && currentUser.getUserId().equals(customerId));
    }

    boolean isAdminSession() {
        return role() == Role.ADMIN;
    }

    /** The real customer record (for other services' checks); never handed outside the service package. */
    Optional<Customer> findCustomerRecord(String customerId) {
        return customers().filter(c -> c.getUserId().equals(customerId)).findFirst();
    }

    private java.util.stream.Stream<Customer> customers() {
        return users.stream().filter(u -> u instanceof Customer).map(u -> (Customer) u);
    }

    /** What callers get: a copy they can't use to change the stored account, without the password hash. */
    private static <T extends User> T publicCopy(T user) {
        @SuppressWarnings("unchecked")
        T copy = (T) user.copy();
        copy.setPassword(null);
        return copy;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public boolean isUsernameTaken(String username) {
        return users.stream().anyMatch(u -> u.getUsername().equalsIgnoreCase(username.trim()));
    }

    private void validateNewAccount(String username, String password, String fullName) throws ValidationException {
        if (username == null || username.trim().isEmpty()) {
            throw new ValidationException("Username is required");
        }
        if (!username.trim().matches("[A-Za-z0-9_.]{3,20}")) {
            throw new ValidationException("Username must be 3-20 letters, numbers, dots or underscores");
        }
        if (isUsernameTaken(username)) {
            throw new ValidationException("Username already exists");
        }
        validatePasswordStrength(password);
        validateFullName(fullName);
    }

    /** Simple email shape check: something@domain.tld, no spaces. */
    private static final String EMAIL_PATTERN = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+";

    private static void validateEmail(String email, boolean required) throws ValidationException {
        if (email == null || email.trim().isEmpty()) {
            if (required) throw new ValidationException("Email is required");
            return;
        }
        if (!email.trim().matches(EMAIL_PATTERN)) {
            throw new ValidationException("Please enter a valid email address (e.g. name@example.com)");
        }
    }

    /** License is required and must not belong to another customer (case and spaces ignored). */
    private void validateLicense(String licenseNumber, String ownUserId) throws ValidationException {
        if (licenseNumber == null || licenseNumber.trim().isEmpty()) {
            throw new ValidationException("Driver's license number is required");
        }
        String normalized = licenseNumber.trim().toUpperCase();
        boolean taken = customers()
                .filter(c -> !c.getUserId().equals(ownUserId))
                .anyMatch(c -> c.getLicenseNumber() != null && c.getLicenseNumber().trim().toUpperCase().equals(normalized));
        if (taken) {
            throw new ValidationException("License number " + licenseNumber.trim() + " is already registered to another customer");
        }
    }

    private static void validateFullName(String fullName) throws ValidationException {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new ValidationException("Full name is required");
        }
    }

    private static String trimmed(String s) {
        return s == null ? "" : s.trim();
    }

    public Customer registerCustomer(String username, String password, String fullName, String email, String phone, String licenseNumber) throws ValidationException {
        validateNewAccount(username, password, fullName);
        validateEmail(email, true);
        validateLicense(licenseNumber, null);
        Customer customer = new Customer(nextCustomerId(), username.trim(), PasswordHasher.hash(password),
                fullName.trim(), email.trim(), trimmed(phone), licenseNumber.trim());
        addAndSave(customer);
        audit.recordAs(customer.getUserId(), AuditLog.CUSTOMER_REGISTERED, null, "signed up: " + describe(customer));
        return publicCopy(customer);
    }

    /**
     * Creates an admin account. An existing admin must approve it with their own username and
     * password, so nobody can give themselves admin access from the public sign-up screen.
     */
    public Admin registerAdmin(String username, String password, String fullName, String email, String phone,
                               String approverUsername, String approverPassword) throws ValidationException {
        // the approver's password counts towards the same lockout as logging in
        String key = lockKey(approverUsername);
        String locked = lockMessage(key);
        if (locked != null) {
            throw new ValidationException(locked);
        }
        User found = findByUsername(approverUsername);
        String approverPw = approverPassword == null ? "" : approverPassword;
        // a customer's or unknown username is checked against the dummy hash (always false, same time)
        boolean approved = passwordMatches(found instanceof Admin ? found : null, approverPw);
        User approver = approved ? found : null;
        if (approver == null) {
            String nowLocked = recordFailure(key);
            throw new ValidationException(nowLocked != null ? nowLocked : "Admin accounts must be approved by an existing admin. "
                    + "The approving admin's username or password is incorrect.");
        }
        attempts.remove(key);
        validateNewAccount(username, password, fullName);
        validateEmail(email, true);
        Admin admin = new Admin(nextAdminId(), username.trim(), PasswordHasher.hash(password),
                fullName.trim(), email.trim(), trimmed(phone), "ADMIN");
        addAndSave(admin);
        // recorded under the approving admin, who is responsible for the new account
        audit.recordAs(approver.getUserId(), AuditLog.ADMIN_CREATED, null, "new admin " + admin.getUserId() + " "
                + admin.getFullName() + " (username " + admin.getUsername() + "), approved by " + approver.getUsername());
        return publicCopy(admin);
    }

    private void addAndSave(User user) throws ValidationException {
        users.add(user);
        try {
            persist();
        } catch (ValidationException e) {
            users.remove(user);
            throw e;
        }
    }

    /** Admin-side add (Manage Customers): name, contact number and license are required; email is optional. */
    public Customer addCustomer(String fullName, String email, String phone, String licenseNumber) throws ValidationException {
        String denied = adminOnly("add customers");
        if (denied != null) throw new ValidationException(denied);
        validateCustomerDetails(fullName, email, phone, licenseNumber, null);
        String newUserId = nextCustomerId();
        String username = "cust" + newUserId.substring(3);
        String temporary = randomTemporaryPassword();
        Customer customer = new Customer(newUserId, username, PasswordHasher.hash(temporary),
                fullName.trim(), trimmed(email), phone.trim(), licenseNumber.trim());
        // the starting password was shown to the admin, so the customer must change it at first login
        customer.setMustChangePassword(true);
        addAndSave(customer);
        temporaryPasswords.put(newUserId, temporary);
        audit.record(AuditLog.CUSTOMER_ADDED, null, describe(customer));
        return publicCopy(customer);
    }

    /**
     * The random starting password of a customer just added with {@link #addCustomer}. It can be
     * read once (admin only), so the admin can hand it over; after that it is gone from memory.
     */
    public String takeTemporaryPassword(String userId) throws ValidationException {
        String denied = adminOnly("see starting passwords");
        if (denied != null) throw new ValidationException(denied);
        return temporaryPasswords.remove(userId);
    }

    /** Ten letters and digits from a secure random source; always meets PASSWORD_RULE. */
    static String randomTemporaryPassword() {
        char[] out = new char[TEMPORARY_PASSWORD_LENGTH];
        String all = TEMPORARY_LETTERS + TEMPORARY_DIGITS;
        for (int i = 0; i < out.length; i++) {
            out[i] = all.charAt(RANDOM.nextInt(all.length()));
        }
        // make sure there is at least one letter and one digit, at random places
        int letterAt = RANDOM.nextInt(out.length);
        int digitAt = (letterAt + 1 + RANDOM.nextInt(out.length - 1)) % out.length;
        out[letterAt] = TEMPORARY_LETTERS.charAt(RANDOM.nextInt(TEMPORARY_LETTERS.length()));
        out[digitAt] = TEMPORARY_DIGITS.charAt(RANDOM.nextInt(TEMPORARY_DIGITS.length()));
        return new String(out);
    }

    public void updateCustomer(String userId, String fullName, String email, String phone, String licenseNumber) throws ValidationException {
        String denied = adminOrSelf(userId, "edit customer details");
        if (denied != null) throw new ValidationException(denied);
        Customer customer = findCustomer(userId);
        validateCustomerDetails(fullName, email, phone, licenseNumber, userId);
        requireWritable();
        String before = describe(customer);
        customer.setFullName(fullName.trim());
        customer.setEmail(trimmed(email));
        customer.setPhone(phone.trim());
        customer.setLicenseNumber(licenseNumber.trim());
        persist();
        audit.record(AuditLog.CUSTOMER_EDITED, null, "was " + before + "; now " + describe(customer));
    }

    private void validateCustomerDetails(String fullName, String email, String phone, String licenseNumber,
                                         String ownUserId) throws ValidationException {
        validateFullName(fullName);
        if (phone == null || phone.trim().isEmpty()) {
            throw new ValidationException("Contact number is required");
        }
        validateEmail(email, false);
        validateLicense(licenseNumber, ownUserId);
    }

    /**
     * Deactivates a customer instead of deleting them, so their bookings, payments and name stay in
     * the records. Refused while they have a confirmed or picked-up rental, or owe money on any booking;
     * the message lists which bookings are in the way. A deactivated customer can't log in or book.
     */
    public void deactivateCustomer(String userId, BookingService bookings) throws ValidationException {
        if (bookings == null) throw new IllegalArgumentException("bookings is required to check open rentals");
        String denied = adminOnly("deactivate customers");
        if (denied != null) throw new ValidationException(denied);
        requireWritable();
        Customer customer = findCustomer(userId);
        if (!customer.isActive()) {
            throw new ValidationException(customer.getFullName() + " is already deactivated");
        }

        List<String> blocking = new ArrayList<>();
        for (Booking b : bookings.getBookingsByCustomer(userId)) {
            List<String> why = new ArrayList<>();
            if (b.getStatus() == BookingStatus.CONFIRMED) why.add("confirmed for " + b.getStartDate() + " to " + b.getEndDate());
            if (b.getStatus() == BookingStatus.ACTIVE) why.add("vehicle not returned yet");
            if (b.getBalanceDue().signum() > 0) why.add(String.format("owes ₱%,.2f", b.getBalanceDue()));
            if (!why.isEmpty()) blocking.add(b.getBookingId() + " (" + String.join(", ", why) + ")");
        }
        if (!blocking.isEmpty()) {
            throw new ValidationException("Can't deactivate " + customer.getFullName() + " yet:\n- "
                    + String.join("\n- ", blocking)
                    + "\nCancel or return these rentals and collect any balance first.");
        }

        customer.setActive(false);
        try {
            persist();
        } catch (ValidationException e) {
            customer.setActive(true);
            throw e;
        }
        audit.record(AuditLog.CUSTOMER_DEACTIVATED, null, describe(customer));
    }

    /** Lets a deactivated customer log in and book again. */
    public void reactivateCustomer(String userId) throws ValidationException {
        String denied = adminOnly("reactivate customers");
        if (denied != null) throw new ValidationException(denied);
        requireWritable();
        Customer customer = findCustomer(userId);
        if (customer.isActive()) {
            throw new ValidationException(customer.getFullName() + " is already active");
        }
        customer.setActive(true);
        try {
            persist();
        } catch (ValidationException e) {
            customer.setActive(false);
            throw e;
        }
        audit.record(AuditLog.CUSTOMER_REACTIVATED, null, describe(customer));
    }

    private Customer findCustomer(String userId) throws ValidationException {
        return findCustomerRecord(userId)
                .orElseThrow(() -> new ValidationException("Customer not found: " + userId));
    }

    /**
     * Accounts the logged-in user may see, as copies without password hashes: every account for an
     * admin, only their own for a customer, none otherwise.
     */
    public List<User> getAllUsers() {
        return users.stream()
                .filter(u -> role() == Role.ADMIN || (role() == Role.CUSTOMER && u == currentUser))
                .map(AuthService::publicCopy)
                .collect(java.util.stream.Collectors.toList());
    }

    /** Customers the logged-in user may see (all for an admin, themselves for a customer), as copies. */
    public List<Customer> getAllCustomers() {
        return customers()
                .filter(c -> canSee(c.getUserId()))
                .map(AuthService::publicCopy)
                .collect(java.util.stream.Collectors.toList());
    }
}
