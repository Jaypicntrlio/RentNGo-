package model;

public class Admin extends User {
    private String adminLevel;

    public Admin(String userId, String username, String password, String fullName, String email, String phone, String adminLevel) {
        super(userId, username, password, fullName, email, phone);
        this.adminLevel = adminLevel;
    }

    public String getAdminLevel() {
        return adminLevel;
    }

    @Override
    public Admin copy() {
        return copyFlagsTo(new Admin(getUserId(), getUsername(), getPassword(), getFullName(), getEmail(), getPhone(), adminLevel));
    }

    public void setAdminLevel(String adminLevel) {
        this.adminLevel = adminLevel;
    }

    @Override
    public String getRole() {
        return "ADMIN";
    }

    @Override
    public String toString() {
        return "Admin{" +
                "adminLevel='" + adminLevel + '\'' +
                ", userId='" + getUserId() + '\'' +
                ", username='" + getUsername() + '\'' +
                ", fullName='" + getFullName() + '\'' +
                '}';
    }
}