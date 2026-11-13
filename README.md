# RentNGo - Vehicle Rental System

A Java Swing desktop application for managing vehicle rentals.

## How to Run

**You need:** a JDK (Java Development Kit), version 17 or newer, not just a JRE. Check it in a
terminal: `javac -version` should print a version number. If it says "not found", install a JDK
(for example [Eclipse Temurin](https://adoptium.net/)) and open a new terminal.

**1. Get the code**

```bash
git clone https://github.com/Jaypicntrlio/RentNGo-.git
cd RentNGo-
```

Or on GitHub click **Code → Download ZIP** and unzip it.

**2. Start the app** (pick one)

- **Windows, easiest:** double-click `run.bat`. It compiles everything, then opens the login screen.
  From a terminal in the project folder: `.\run.bat`
- **VS Code:** open the project folder (or `RentNGo.code-workspace`), open `src/app/RentNGo.java` and
  press **F5**, or use Code Runner's **Run Code** ▷ button (the project settings make it build the
  whole project, not just one file).
- **Any system, by hand:** see [Building & Running](#building--running) below.

**3. Log in** with a demo account:

| Role     | Username | Password   |
|----------|----------|------------|
| Admin    | `admin`  | `admin123` |
| Customer | `john`   | `john123`  |

The `data/` folder (accounts, vehicles, bookings) is created on the first run, with the demo
accounts and vehicles in it. It isn't in Git, so every fresh copy starts with the demo data.

**Common problems**

- *"javac was not found"*: no JDK installed, or it isn't on PATH. Install one and reopen the terminal.
- *"package gui does not exist"*: something compiled only one file. Use `run.bat` or the full
  `javac` command below, which compile the whole `src/` folder.
- *"RentNGo is already running on this computer"*: close the other RentNGo window first.

## Features

- **User Authentication**: Login and sign-up for Admin and Customer roles. New admin accounts must be
  approved by an existing admin.
- **Login security**:
  - Passwords are stored as salted SHA-256 hashes with 100,000 rounds (`100000$salt$hash`). Older
    `salt$hash` values (10,000 rounds) still work and are upgraded automatically at the next login.
  - New passwords need at least 8 characters with a letter and a number (existing ones keep working).
  - After 5 wrong passwords a username is locked for 5 minutes ("Too many attempts. Try again in X
    minutes."); this also covers the approving-admin password when creating an admin account.
    Unknown usernames get the same message as wrong passwords.
  - **Change Password** is in both dashboards. Customers added by an admin start with a random
    10-character password, shown to the admin once, and must choose their own password before they can continue.
- **Vehicle Management**: Add, edit, delete vehicles (Cars, SUVs, Trucks) with photos and an
  "In Service" switch for vehicles under maintenance.
- **Booking System**: Rent vehicles by date with automatic cost calculation; availability is
  worked out from bookings, so a vehicle can be booked for any free dates.
- **Returns & Payments**: Mark pick-ups and returns, automatic late fees, payments, refunds on
  cancellation, and receipts listing every payment and refund for the booking (Save as TXT).
  - Card and GCash are charged the exact amount due; only Cash can be more (for change), by at
    most ₱100,000.
  - A payment must be for the booking's own customer.
  - **Cancellation policy**: cancelling less than 24 hours before the start date keeps a 20% fee
    (`BookingService.CANCELLATION_FEE_RATE`) of what was paid and refunds the rest; cancelling
    earlier refunds everything. The fee and refund are shown before the customer confirms.
- **Admin Dashboard**: Revenue overview, manage vehicles, customers, rentals, returns, payments
  and rental records.
- **Customer deactivation**: Customers are deactivated instead of deleted, so their bookings and
  payments keep their name. Deactivation is refused while they have a confirmed or picked-up rental
  or owe money (the message lists the bookings). Deactivated customers can't log in or book; an admin
  can reactivate them (Customers → Show inactive → Reactivate).
- **Audit Log**: Every booking, pick-up, return, payment, refund/cancellation, vehicle change,
  customer change (including deactivation) and new admin account is recorded in `data/audit.txt` with the time and the user
  who did it (sidebar → Audit Log: read-only, newest first, searchable). Lines are only ever added.
- **Reports**: Total revenue, revenue this month, most-rented vehicle, active rentals and
  bookings per vehicle type (sidebar → Reports).
- **CSV export** (Rental Records): values are quoted with inner quotes doubled, values starting with
  `= + - @` (or a tab/return) get a leading `'` so Excel shows them as text instead of running a
  formula, and the file is UTF-8 with a BOM so ₱ displays correctly.
- **Permissions in the services**: every service method checks the logged-in user, whichever
  window calls it. Admin-only: payments, pick-ups, returns, status changes, vehicle and customer
  management. Customers can only book, cancel and edit for themselves, and only see their own data.
  Someone who must change their password can do nothing else first. Services hand out copies
  (never password hashes), so changing a returned object never changes saved data.
- **Privacy**: Customers only ever see (and export) their own rental records; admins see all.
  Logging out closes every window of the session, and admin tool windows check that an admin is
  still logged in before saving, paying, returning or deleting.
- **Customer Dashboard**: Browse available vehicles with search (brand/model), a type filter and a
  max price slider; click a column header to sort. Make and cancel bookings, view history.
- **Light / Dark mode**: Modern [FlatLaf](https://www.formdev.com/flatlaf/) look with a Light/Dark
  button on both dashboards. The choice is remembered; the login screen always stays dark.
- **Saved Data**: Everything is saved automatically to the `data/` folder.

## Vehicle Types & Pricing

Prices are in pesos (₱). Money is stored as `BigDecimal`, rounded to 2 decimals.

- **Car**: Base price, 10% discount for Electric vehicles
- **SUV**: 15% surcharge for 4-wheel drive
- **Truck**: 20% surcharge for load capacity > 5 tons

A rental is charged per day **between** the start and end dates (Mon → Tue is 1 day); the end
date must be after the start date. The end date is the **return day**, so availability uses the same
rule: a car booked 1st–5th can be booked again from the 5th (same-day handover), and two bookings
clash only if they share at least one day. A rental that is overdue and not yet returned blocks the
car through today. Returning after the end date adds a late fee of **1.5× the
daily rate** for each extra day.

## Booking Status Flow

```
PENDING ──► CONFIRMED ──► ACTIVE ──► COMPLETED
   │            │
   └────────────┴──────► CANCELLED
```

| From      | Can become            | How                                                        |
|-----------|-----------------------|------------------------------------------------------------|
| PENDING   | CONFIRMED, CANCELLED  | New bookings are created as CONFIRMED                       |
| CONFIRMED | ACTIVE, CANCELLED     | **Mark Picked Up** (Return Vehicle window), or cancel before the start date |
| ACTIVE    | COMPLETED             | **Confirm Return**: adds late fee / extra charges, takes the payment (Cash, Card or GCash) and shows a receipt that can be saved as TXT. Records which admin processed it and when. A return date other than today needs a reason (at least 10 characters), saved with the booking and in the audit log |
| COMPLETED | — (final)             |                                                            |
| CANCELLED | — (final)             | Anything already paid is recorded as a refund               |

Any other change is rejected with a `BookingException`. Returning a CONFIRMED rental that was
never marked as picked up counts as picked up first (CONFIRMED → ACTIVE → COMPLETED).

## Libraries (`lib/`)

| Jar | Used for | Download |
|-----|----------|----------|
| `flatlaf-3.7.2.jar` | Modern look-and-feel, Light/Dark themes | [Maven Central](https://repo1.maven.org/maven2/com/formdev/flatlaf/3.7.2/flatlaf-3.7.2.jar) |
| `junit-platform-console-standalone-1.13.4.jar` | JUnit 5 tests (tests only) | [Maven Central](https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.13.4/junit-platform-console-standalone-1.13.4.jar) |

To add a jar yourself: create a `lib` folder next to `src`, download the jar into it, and include
`lib/*` on the classpath as shown below. FlatLaf is loaded by class name, so if its jar is missing the
app still runs with the plain Windows look (and prints a note); the Light/Dark colors still switch.

## Building & Running

Requires Java 17 or newer. Run these from the `RentNGo` folder.

```bash
# Compile all sources into out/
javac -encoding UTF-8 -cp "lib/*" -d out $(find src -name "*.java")

# Copy resources (login background image) next to the compiled classes
mkdir -p out/resources && cp -r src/resources/. out/resources/

# Run (the main class is app.RentNGo); lib/* puts FlatLaf on the classpath
java --enable-native-access=ALL-UNNAMED -cp "out;lib/*" app.RentNGo
```

On Windows PowerShell:

```powershell
javac -encoding UTF-8 -cp "lib/*" -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
Copy-Item -Recurse -Force src\resources out\
java --enable-native-access=ALL-UNNAMED -cp "out;lib/*" app.RentNGo
```

On macOS/Linux use `:` instead of `;` in the classpath (`"out:lib/*"`). Keep the quotes around
`lib/*` so the shell doesn't expand it; Java expands it to every jar in `lib/`.
`--enable-native-access=ALL-UNNAMED` only silences a warning newer Java versions print when FlatLaf
loads its small native library for the window title bar.

In VS Code, open the `RentNGo` folder itself (not a parent folder), so its `.vscode/` settings are
used: they already add `lib/*.jar` to the project and set up **F5** (`.vscode/launch.json`) and Code
Runner. Then run `src/app/RentNGo.java`.

Images such as the login background are loaded from the classpath (`/resources/...`), so they
must be in `out/resources/` when running from `out`. If the login image is missing, the login
screen falls back to a plain gradient background.

Saved data (accounts, vehicles, bookings, payments, and the Light/Dark choice in
`settings.properties`) is kept in the `data/` folder and photos in `images/`; both are created
automatically on first run.

If a data file is damaged:

- **A bad line** is skipped on its own and reported at startup (e.g. `accounts.txt line 7 skipped:
  …`); everything else still loads. A `.corrupt-…` copy of the original file is kept, because the
  skipped lines are dropped the next time that file is saved.
- **Demo accounts and vehicles** are only created on the very first run, when the file doesn't exist.
  They are never created over an existing file.
- **A file that can't be read at all** (or an accounts file with no admin account) is left
  untouched: nothing is saved to it and changes are refused until it is fixed and RentNGo is
  restarted. If the accounts can't be loaded, the app shows the error and closes instead of
  opening the login screen.
- **Only one copy of RentNGo** can use the `data/` folder at a time: it holds a lock on
  `data/.rentngo.lock` while running (released on exit, or by the OS if it crashes). Starting a second
  copy shows "RentNGo is already running on this computer. Close the other window first."
- **A file changed on disk** after RentNGo loaded it (by another program or a hand edit) is never
  overwritten: the save is refused, RentNGo shows "Data was changed by another program. Please restart
  RentNGo.", and that part of the data stays read-only until you restart.
- **Booking and payment numbers** are saved as `#seq` lines (like the CUS/ADM account numbers), so
  a number is never handed out twice, even after a bad line is skipped.

### Tests

Compile the app first (above). The tests use temporary files, so your real `data/` folder is never
touched. (On macOS/Linux use `:` instead of `;` in the classpaths.)

**JUnit 5** (`test/service/`): BookingService (overlapping bookings, Car/SUV/Truck pricing, status
rules, late fees, who can see which bookings), ReportService, and recovery from damaged data files
(DataFileRecoveryTest).

```bash
javac -encoding UTF-8 -cp "out;lib/junit-platform-console-standalone-1.13.4.jar" -d test-out test/service/*.java test/gui/*.java
java -jar lib/junit-platform-console-standalone-1.13.4.jar execute -cp "out;test-out;lib/flatlaf-3.7.2.jar" --select-package service --select-package gui
```

**Plain-Java tests** (`test/RentNGoTests.java`, no library needed):

```bash
javac -encoding UTF-8 -cp out -d test-out test/RentNGoTests.java
java -cp "test-out;out" RentNGoTests
```

## Project Structure

```
RentNGo/
├── src/
│   ├── app/
│   │   └── RentNGo.java              # Main entry point (opens saved data, shows login)
│   ├── model/                        # Data models
│   │   ├── User.java                 # Base user class (stores a password hash, never the password)
│   │   ├── Admin.java                # Admin user
│   │   ├── Customer.java             # Customer user
│   │   ├── Vehicle.java              # Base vehicle class (price, "In Service" flag, photo)
│   │   ├── Car.java                  # Car vehicle type
│   │   ├── SUV.java                  # SUV vehicle type
│   │   ├── Truck.java                # Truck vehicle type
│   │   ├── Booking.java              # Booking: dates, status, charges, payments, day counting
│   │   ├── BookingStatus.java        # PENDING / CONFIRMED / ACTIVE / COMPLETED / CANCELLED
│   │   ├── Payment.java              # Payment or refund record
│   │   ├── Money.java                # BigDecimal helpers (2-decimal rounding)
│   │   └── exceptions/               # RentNGoException, ValidationException, BookingException,
│   │                                 # AuthenticationException, VehicleNotFoundException
│   ├── service/                      # Business logic and saving
│   │   ├── DataStore.java            # Opens all saved data at startup, reports damaged files
│   │   ├── AuthService.java          # Login, sign-up, customer accounts and their validation
│   │   ├── VehicleService.java       # Vehicles and their validation
│   │   ├── BookingService.java       # Bookings, availability, status rules, returns, late fees
│   │   ├── PaymentService.java       # Payments and refunds
│   │   ├── ReportService.java        # Figures for the admin Reports view
│   │   ├── AppLock.java              # One copy of RentNGo per data folder (data/.rentngo.lock)
│   │   ├── AuditLog.java             # Append-only who-did-what log (data/audit.txt)
│   │   ├── CsvWriter.java            # Safe CSV export: quoting, formula guard, UTF-8 with BOM
│   │   ├── AccountStore.java         # Reads/writes data/accounts.txt
│   │   ├── TsvFile.java              # Tab-separated file reading/writing used for all data files
│   │   ├── PasswordHasher.java       # Salted, iterated SHA-256 hashes (round count stored)
│   │   └── ProjectPaths.java         # Locates the project's data/ and images/ folders
│   ├── gui/                          # Swing UI
│   │   ├── UIConstants.java          # Colors (dark/light palette), fonts, component helpers, logo
│   │   ├── Theme.java                # FlatLaf Light/Dark switching and the saved choice
│   │   ├── WindowSession.java        # Closes session windows on logout; admin-only action check
│   │   ├── LoginFrame.java           # Login screen
│   │   ├── RegisterDialog.java       # Create Account dialog (customer or approved admin)
│   │   ├── ChangePasswordDialog.java # Change password (forced at first login for new customers)
│   │   ├── AdminDashboardFrame.java  # Admin main window with sidebar
│   │   ├── DashboardPanel.java       # Admin sales overview (KPIs and charts)
│   │   ├── ReportsPanel.java         # Admin Reports view
│   │   ├── AuditLogPanel.java        # Admin Audit Log view (read-only, search)
│   │   ├── LineAreaChartPanel.java   # Monthly revenue chart
│   │   ├── BarChartPanel.java        # Revenue by vehicle type chart
│   │   ├── DonutChartPanel.java      # Donut chart (not currently used)
│   │   ├── RadialGaugePanel.java     # Radial gauge (not currently used)
│   │   ├── ManageVehiclesFrame.java  # Vehicle list, details and photos
│   │   ├── VehicleDialog.java        # Add/Edit vehicle dialog
│   │   ├── ManageCustomersFrame.java # Customer list and details
│   │   ├── CustomerDialog.java       # Add/Edit customer dialog (admin)
│   │   ├── RentVehicleFrame.java     # Admin: rent a vehicle to a customer
│   │   ├── ReturnVehicleFrame.java   # Admin: mark picked up / return + payment
│   │   ├── PaymentFrame.java         # Admin: take payments for unpaid rentals
│   │   ├── ReceiptDialog.java        # Payment receipt with "Save as TXT"
│   │   ├── RentalRecordsDialog.java  # Rental records with search, filter and export
│   │   ├── CustomerDashboardFrame.java # Customer dashboard
│   │   ├── RentDialog.java           # Customer: rent a vehicle
│   │   ├── VehicleImages.java        # Loads and stores vehicle photos
│   │   ├── ProfilePhotos.java        # Profile pictures and round avatars
│   │   └── SilhouettePanel.java      # Vehicle photo panel (drawn placeholder if no photo)
│   └── resources/
│       └── login-bg.jpg              # Login screen background
├── lib/                              # flatlaf and JUnit jars
├── test/
│   ├── RentNGoTests.java             # Plain-Java tests for the rental rules
│   ├── service/
│   │   ├── BookingServiceTest.java   # JUnit 5: overlaps, pricing, status rules, late fees
│   │   ├── ReportServiceTest.java    # JUnit 5: report figures
│   │   ├── DataFileRecoveryTest.java # JUnit 5: bad lines, first-run demo data, ID numbering
│   │   ├── BookingVisibilityTest.java # JUnit 5: customers only see their own bookings
│   │   ├── AppLockTest.java          # JUnit 5: a second copy can't lock the data folder
│   │   ├── AppLockProbe.java         # (helper run as a second process by AppLockTest)
│   │   ├── DataChangedOnDiskTest.java # JUnit 5: files changed on disk are never overwritten
│   │   ├── AuditLogTest.java         # JUnit 5: backdated returns need a reason; actions are logged
│   │   ├── CustomerDeactivationTest.java # JUnit 5: deactivate/reactivate rules, login and booking blocks
│   │   ├── LoginSecurityTest.java    # JUnit 5: lockout, change password, forced change, hash upgrade
│   │   ├── PaymentRulesTest.java     # JUnit 5: exact card/GCash, cash limit, customer match, cancellation fee
│   │   ├── BookingAvailabilityTest.java # JUnit 5: same-day handover, overlaps, overdue rentals
│   │   ├── CsvWriterTest.java        # JUnit 5: quotes, formulas, commas, newlines, peso sign
│   │   ├── AccessControlTest.java    # JUnit 5: permission checks inside the services
│   │   ├── ReviewFixesTest.java      # JUnit 5: refused saves are undone, safe deletes, random passwords
│   │   └── Stores.java               # (helper: opens a test data folder with the admin logged in)
│   └── gui/
│       ├── WindowSessionTest.java    # JUnit 5: closing session windows, admin session check
│       └── RentalRecordsExportTest.java # JUnit 5: Rental Records export is escaped
├── run.bat                           # Windows: compile and start in one step
├── data/                             # Saved data (created at runtime, not in Git)
└── images/                           # Vehicle photos and images/profiles/ (profile pictures)
```
