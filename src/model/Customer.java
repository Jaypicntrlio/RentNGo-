package model;

import java.time.LocalDate;

public class Customer extends User {
    private String licenseNumber;
    private LocalDate registeredDate;
    /** Loyalty points: earned on completed, paid rentals and spent as a discount (1 point = ₱1). */
    private int loyaltyPoints;

    public Customer(String userId, String username, String password, String fullName, String email, String phone, String licenseNumber) {
        super(userId, username, password, fullName, email, phone);
        this.licenseNumber = licenseNumber;
        this.registeredDate = LocalDate.now();
    }

    public LocalDate getRegisteredDate() {
        return registeredDate;
    }

    @Override
    public Customer copy() {
        Customer c = new Customer(getUserId(), getUsername(), getPassword(), getFullName(), getEmail(), getPhone(), licenseNumber);
        c.registeredDate = registeredDate;
        c.loyaltyPoints = loyaltyPoints;
        return copyFlagsTo(c);
    }

    public void setRegisteredDate(LocalDate registeredDate) {
        this.registeredDate = registeredDate;
    }

    public int getLoyaltyPoints() {
        return loyaltyPoints;
    }

    public void setLoyaltyPoints(int loyaltyPoints) {
        this.loyaltyPoints = loyaltyPoints;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    @Override
    public String getRole() {
        return "CUSTOMER";
    }

    @Override
    public String toString() {
        return "Customer{" +
                "licenseNumber='" + licenseNumber + '\'' +
                ", userId='" + getUserId() + '\'' +
                ", username='" + getUsername() + '\'' +
                ", fullName='" + getFullName() + '\'' +
                '}';
    }
}