package model;

import java.time.LocalDate;

public class Customer extends User {
    private String licenseNumber;
    private LocalDate registeredDate;

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
        return copyFlagsTo(c);
    }

    public void setRegisteredDate(LocalDate registeredDate) {
        this.registeredDate = registeredDate;
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