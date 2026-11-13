package model;

public abstract class User {
    /** Identity never changes after creation, so there are no setters for these two. */
    private final String userId;
    private final String username;
    private String password;
    private String fullName;
    private String email;
    private String phone;
    /** False once the account is deactivated: it can't log in or book, but its history is kept. */
    private boolean active = true;
    /** True for accounts given a shared starting password; they must pick their own at first login. */
    private boolean mustChangePassword;

    public User(String userId, String username, String password, String fullName, String email, String phone) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
    }

    /** An independent copy (subclasses add their own fields). */
    public abstract User copy();

    /** Copies the fields every user has onto a new copy. */
    protected <T extends User> T copyFlagsTo(T copy) {
        copy.setActive(active);
        copy.setMustChangePassword(mustChangePassword);
        return copy;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public abstract String getRole();

    @Override
    public String toString() {
        return "User{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                '}';
    }
}