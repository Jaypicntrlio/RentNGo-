# Code Review: RentNGo, Four OOP Principles

## Metadata

| | |
|---|---|
| Mode | General, whole project (the folder is not a git repo, so there is no diff; every source file was reviewed) |
| Target | `Documents/RentNGo/src` (58 `.java` files, about 12,160 lines) |
| Date | 2026-11-03 |
| Stack | Java, Swing + FlatLaf GUI, JUnit 5 tests, tab-separated text files for storage |
| Checks run | Encapsulation, Inheritance, Polymorphism, Abstraction (requested), plus code quality where it touches them |
| Checks skipped | Security, performance, tests, accessibility, docs (not requested) |
| Build | `model` + `service` packages compile cleanly with `javac` |

## Review Process

- [x] Preflight (no git repo found, so whole-project review instead of a diff)
- [x] Source gathered (`src/app`, `src/model`, `src/service`, `src/gui`)
- [x] Stack detected
- [x] Scope set by the request: the four OOP principles
- [x] Every model class read in full; services and GUI searched for type checks, overrides and access patterns
- [x] Claims checked by compiling the model and running a small probe (results quoted below)
- [x] Findings de-duplicated
- [x] Verdict

## Verdict: ✅ APPROVE

All four principles are clearly present and used for real work, not just declared. The `Vehicle` and `User` hierarchies, the exception hierarchy and the copy-on-read services are textbook examples. The main gap is polymorphism: the project computes rental cost polymorphically, but in nine places it still asks "is this a Car, SUV or Truck?" with `instanceof` instead of letting each subclass answer. Nothing blocks; the Medium items are worth fixing before a defense or demo because a grader looking for OOP will notice them.

## Finding Counts

| Severity | Count |
|---|---|
| 🔴 Critical | 0 |
| 🟠 High | 0 |
| 🟡 Medium | 3 |
| 💭 Low | 7 |
| ⚠️ Manual | 1 |

---

## Where each principle appears in the code

Use this section when you need to point at the four principles.

### 1. Encapsulation ✅ (strong)

| Evidence | Location |
|---|---|
| Every field in every model class is `private`, accessed through getters and setters | `User.java:4-13`, `Vehicle.java:6-14`, `Booking.java:8-24`, `Payment.java:7-13` |
| Setters guard their data: money is always rounded to 2 decimals | `Booking.java:95-97`, `Booking.java:132-155`, `Vehicle.java:82-86` |
| `Payment` is read-only after creation (no setters at all) | `Payment.java` |
| Services keep the real objects private and hand out copies, so a window can't change stored data | `VehicleService.java:211-230`, `BookingService.java:292`, `BookingService.java:508-535` |
| Copies of users have the password hash removed before leaving `AuthService` | `AuthService.java:391-397` |
| Permission helpers are package-private, so only the `service` package can call them | `AuthService.java:354-385` |
| `Money` is a `final` class with a `private` constructor, so it can't be subclassed or instantiated | `Money.java:7-11` |

### 2. Inheritance ✅

| Evidence | Location |
|---|---|
| `User` → `Admin`, `Customer` | `Admin.java:3`, `Customer.java:5` |
| `Vehicle` → `Car`, `SUV`, `Truck` | `Car.java:5`, `SUV.java:5`, `Truck.java:5` |
| `RentNGoException` → `AuthenticationException`, `BookingException`, `ValidationException`, `VehicleNotFoundException` | `model/exceptions/` |
| Subclass constructors reuse the parent's with `super(...)` | `Car.java:10`, `Admin.java:7`, `Customer.java:10` |
| Subclasses reuse `protected` parent helpers instead of repeating code | `Vehicle.baseCost` (`Vehicle.java:122`), `Vehicle.copyCommonTo` (`Vehicle.java:35`), `User.copyFlagsTo` (`User.java:28`) |
| GUI windows extend Swing classes (`JFrame`, `JDialog`, `JPanel`) | e.g. `AdminDashboardFrame.java:19`, `RentDialog.java:22`, `BarChartPanel.java:10` |

### 3. Polymorphism ✅ (present, under-used, see findings)

| Evidence | Location |
|---|---|
| **Overriding / runtime dispatch:** each vehicle prices itself differently (electric car 10% off, 4WD SUV +15%, heavy truck +20%) and callers just call `vehicle.calculateRentalCost(days)` | `Car.java:41-48`, `SUV.java:41-48`, `Truck.java:41-48`; called in `BookingService.java:275`, `RentDialog.java:243`, `RentVehicleFrame.java:280` |
| `getRole()`, `getVehicleCategory()`, `toString()` overridden in every subclass | `Admin.java:24-37`, `Customer.java:38-51`, `Car.java:31-60` |
| `copy()` overridden with covariant return types (`Car copy()` overrides `Vehicle copy()`) and called through the parent type | `Car.java:37`, `Admin.java:16`; called in `VehicleService.java:214`, `AuthService.java:394` |
| **Overloading:** two `Payment` constructors, two `RentNGoException` constructors, two `DataStore` constructors | `Payment.java:15` and `:26`, `RentNGoException.java:4` and `:8`, `DataStore.java:26` and `:31` |
| Interface polymorphism: several icon classes implement Swing's `Icon` | `ProfilePhotos.java:69`, `UIConstants.java:888`, `Theme.java:163` |

Probe run against your compiled model (one loop, one method call, three behaviours):

```
Car 3 days -> 12150.00
SUV 3 days -> 12075.00
Truck 3 days -> 11520.00
```

### 4. Abstraction ✅

| Evidence | Location |
|---|---|
| `User` is `abstract` with abstract `getRole()` and `copy()`; you can't create a plain "User" | `User.java:3`, `:25`, `:98` |
| `Vehicle` is `abstract` with abstract `getVehicleCategory()`, `calculateRentalCost()` and `copy()` | `Vehicle.java:5`, `:32`, `:116`, `:119` |
| Services hide how data is stored: GUI code asks `VehicleService` for vehicles and never touches `vehicles.txt` | `DataStore.java`, `TsvFile.java` |
| `Money` hides rounding rules behind `round`, `parse`, `times` | `Money.java` |

---

## Polymorphism findings

| # | Severity | File | Line | Issue | Recommendation |
|---|---|---|---|---|---|
| P1 | 🟡 Medium | `VehicleService.java` | 175-184, 258-266, 323-338 | `save()`, `validateVehicle()` and `updateVehicle()` each branch on `instanceof Car / SUV / Truck`. Adding a `Van` means editing all three, and forgetting one fails silently (it would save blank fields). | Move each branch into the subclass as an overridden method (sketch below). |
| P2 | 🟡 Medium | `CustomerDashboardFrame.java`, `RentDialog.java` | 649-661, 211-223 | The same `getVehicleDetails()` `instanceof` chain is written twice, differing only in the separator (`,` vs `·`). | Add `public abstract String getFeatureSummary();` to `Vehicle`; each subclass returns its own text. |
| P3 | 💭 Low | `ManageVehiclesFrame.java`, `RentVehicleFrame.java`, `ReturnVehicleFrame.java`, `DashboardPanel.java` | 208, 262, 368, 276 | Picture type chosen by `instanceof` ternaries or a `switch` on the type string. `CustomerDashboardFrame.java:427` already does it in one line with `SilhouettePanel.Kind.valueOf(v.getType().toUpperCase())`. | Reuse that one line (or a small helper) everywhere. |
| P4 | 💭 Low | `VehicleService.java` | 346-353 | `copyOf()` builds a `Car` to snapshot *any* vehicle, then relabels its type. `Vehicle.copy()` already exists and is polymorphic. | `Vehicle snapshot = existing.copy();` |
| P5 | 💭 Low | `AuthService.java`, `AccountStore.java` | 346, 131-134 | Role is decided with `instanceof Admin`, and the extra saved column with `instanceof Customer / Admin`, although `getRole()` is already overridden for this. | Use `getRole()`, or add an abstract `getExtraField()` to `User`. |

**P1 comment.** I noticed the price rule is nicely polymorphic, but saving, validating and updating still ask each vehicle what it is. Would it make sense to let each subclass own those steps too? Something like:

```java
// Vehicle.java
public abstract String[] detailFields();                       // for save()
public abstract void validateDetails() throws ValidationException;
public abstract void copyDetailsFrom(Vehicle other);           // for updateVehicle()

// Car.java
@Override public String[] detailFields() { return new String[]{ String.valueOf(numberOfDoors), fuelType }; }
@Override public void validateDetails() throws ValidationException {
    if (numberOfDoors <= 0) throw new ValidationException("Number of doors must be at least 1");
    if (fuelType == null || fuelType.isBlank()) throw new ValidationException("Fuel type is required");
}
@Override public void copyDetailsFrom(Vehicle other) {
    Car c = (Car) other; numberOfDoors = c.numberOfDoors; fuelType = c.fuelType;
}

// VehicleService.save()
String[] d = v.detailFields();   // no instanceof
```

Two type checks can reasonably stay. `VehicleDialog.java:329-341` fills type-specific form widgets, which is UI work the model shouldn't know about. And the `switch` in `parseVehicle()` (`VehicleService.java:141-153`) is fine too: reading a type name from a file and choosing which class to build is a factory, and some place has to make that choice. What do you think?

## Abstraction findings

| # | Severity | File | Line | Issue | Recommendation |
|---|---|---|---|---|---|
| A1 | 🟡 Medium | `Vehicle.java` | 10, 70-76 | Vehicle kind is stored twice: the abstract `getVehicleCategory()` and a separate `type` field with a public `setType()`. They can disagree. The probe shows it: a `Car` after `setType("Truck")` prints `class=Car getType=Truck getVehicleCategory=Car`. `type` is what gets written to `vehicles.txt` (`VehicleService.java:185`) and decides the class on reload (`:141`), so a mismatch would reload the car as a truck. No current screen does this (each subclass constructor sets its own type), but `copyOf()` at `VehicleService.java:349` already creates a Car labelled as another type. | Delete the `type` field and `setType()`, and make `getType()` return `getVehicleCategory()`. One source of truth, defined by the class itself. |
| A2 | ⚠️ Manual | whole project | — | No `interface` of your own is declared; abstraction is done with abstract classes, which fully counts. If your instructor's rubric specifically asks for an interface, add a small one. | e.g. `public interface Rentable { BigDecimal calculateRentalCost(int days); }` and `Vehicle implements Rentable`. Check the rubric. |

## Encapsulation findings

| # | Severity | File | Line | Issue | Recommendation |
|---|---|---|---|---|---|
| E1 | 💭 Low | `User.java`, `Booking.java` | 54-56, 62-64; 55-57, 63-65 | Public setters for identity fields (`setUserId`, `setUsername`, `setBookingId`, `setCustomerId`) are never called anywhere. Each one is a way to change an object's identity that nothing needs. | Remove them; the fields can then be `final`. |
| E2 | 💭 Low | `User.java` | 6, 66-72 | The field is named `password` but always holds a hash. `setPassword()` accepts any string, so a caller could store plain text and skip `PasswordHasher` (probe: `password now: plain-text-bypasses-hasher`). All current callers hash first (`AuthService.java:265`, `:317`). | Rename to `passwordHash` / `setPasswordHash` so the intent is in the name. |
| E3 | 💭 Low | `Vehicle.java` | 22, 82-86 | `setPricePerDay()` silently ignores zero or negative prices, while the constructor accepts them (and turns `null` into 0.00). Validation lives in `VehicleService.validateVehicle()`, so there's no live bug, but the class doesn't protect its own rule consistently. | Throw `IllegalArgumentException` in both the constructor and the setter instead of ignoring the value. |

## Inheritance findings

| # | Severity | File | Line | Issue | Recommendation |
|---|---|---|---|---|---|
| I1 | 💭 Low | GUI, `catch` blocks | e.g. `RentDialog.java:270`, `VehicleDialog.java:381` | The exception hierarchy is well built, but `RentNGoException` is never caught as the parent type; every `catch` lists specific subclasses (`BookingException | VehicleNotFoundException`). That is fine code, but it misses the payoff of the hierarchy, and it's an easy demo of polymorphic exception handling. | Where every case shows the same error dialog, `catch (RentNGoException ex)` handles all four subclasses in one clause. |

---

## Manual Checks Required

- **A2:** Check whether your rubric requires an `interface`. If yes, add one; if not, the abstract classes are enough.

## Prioritized Action Items

**Must fix:** none.

**Should address (before a demo or defense):**
1. P1: move the `instanceof` branches in `VehicleService` into overridden `Vehicle` methods.
2. P2: replace the duplicated `getVehicleDetails()` with an abstract `getFeatureSummary()`.
3. A1: remove the `type` field and `setType()`; derive type from the class.

**Nice to have:**
P3, P4, P5, E1, E2, E3, I1.
