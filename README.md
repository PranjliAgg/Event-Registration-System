# Event Registration & Allocation System
### DBMS College Project — MySQL + JavaFX

---

## Project Structure

```
EventRegistration/
├── database/
│   ├── schema.sql          ← Tables, Triggers, Procedures, Sample Data
│   └── queries.sql         ← Basic (A1–A12) & Complex Queries (B1–B12)
│
└── src/
    ├── com/eventregistration/
    │   ├── MainApp.java                        ← JavaFX entry point
    │   ├── db/
    │   │   └── DatabaseConnection.java         ← Singleton JDBC connector
    │   ├── model/
    │   │   ├── Event.java
    │   │   └── User.java
    │   ├── dao/
    │   │   ├── EventDAO.java                   ← Queries + SP calls
    │   │   ├── RegistrationDAO.java            ← SP: register, cancel
    │   │   └── UserDAO.java                    ← Login, register user
    │   └── controller/
    │       ├── LoginController.java
    │       ├── UserDashboardController.java
    │       └── AdminDashboardController.java
    └── resources/
        ├── fxml/
        │   ├── Login.fxml
        │   ├── UserDashboard.fxml
        │   └── AdminDashboard.fxml
        └── css/
            └── style.css
```

---

## Setup Instructions

### Step 1: Database Setup
1. Open MySQL Workbench or terminal.
2. Run `database/schema.sql` to create the database, all tables, triggers, procedures, and sample data.

```sql
source /path/to/database/schema.sql;
```

3. Verify with:
```sql
USE event_registration_db;
SHOW TABLES;
SELECT * FROM EVENTS;
```

### Step 2: Update DB Credentials
Open `src/com/eventregistration/db/DatabaseConnection.java` and update:
```java
private static final String USER     = "root";      // your MySQL username
private static final String PASSWORD = "password";  // your MySQL password
```

### Step 3: Add MySQL Connector JAR
Download **mysql-connector-j-8.x.x.jar** from:
https://dev.mysql.com/downloads/connector/j/

Add it to your project's classpath/module-path.

### Step 4: Add JavaFX SDK
Download JavaFX SDK from https://openjfx.io/

In your IDE (IntelliJ / Eclipse):
- Add JavaFX libraries to the project.
- Set VM options:
```
--module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml
```

### Step 5: Run
Run `MainApp.java` as the main class.

---

## Login Credentials (Sample Data)

| Role  | Email                  | Password   |
|-------|------------------------|------------|
| Admin | admin@college.edu      | password   |
| User  | alice@student.edu      | password   |
| User  | bob@student.edu        | password   |

> **Note:** For the demo, passwords are stored as plain text. In production, use BCrypt hashing.

---

## DBMS Components Covered

### Tables (7)
`USERS`, `EVENT_CATEGORY`, `VENUE`, `EVENTS`, `REGISTRATIONS`, `PAYMENTS`, `EVENT_SCHEDULE`
+ `AUDIT_LOG` (trigger support table)

### Triggers (7)
| Trigger | Description |
|---------|-------------|
| `trg_decrement_seats` | Reduces available_seats on confirmed registration |
| `trg_restore_seats_on_cancel` | Restores seat + auto-promotes waitlisted user |
| `trg_prevent_double_booking` | Blocks duplicate registrations |
| `trg_auto_waitlist` | Sets status to 'waitlisted' when no seats remain |
| `trg_deadline_check` | Blocks registration after deadline |
| `trg_audit_registration` | Logs all new registrations to AUDIT_LOG |
| `trg_audit_payment` | Logs all payment status changes |

### Stored Procedures (5)
| Procedure | Description |
|-----------|-------------|
| `sp_register_user` | Full registration + payment in one transaction |
| `sp_cancel_registration` | Cancels registration + initiates refund |
| `sp_event_report` | Complete event statistics |
| `sp_user_registrations` | User's registration history |
| `sp_search_events` | Filtered event search |

### Basic Queries (A1–A12)
Covers: SELECT with JOIN, WHERE, GROUP BY, ORDER BY, COUNT, LIKE, DATE filtering.

### Complex Queries (B1–B12)
Covers: Window functions (RANK, DENSE_RANK, SUM OVER), subqueries, NOT EXISTS, CASE expressions, multi-table JOINs, HAVING, monthly trends, running totals.

### DB Connectivity (JDBC)
- Singleton pattern for connection management
- `PreparedStatement` for all queries (SQL injection-safe)
- `CallableStatement` for all stored procedure calls
- Transaction management with commit/rollback

---

## Features

### User Portal
- Browse & search upcoming events
- Register for events (with seat check & auto-waitlist)
- Select payment mode and amount
- View personal registration history
- Cancel registrations (triggers auto-refund)

### Admin Portal
- Add new events
- Update event status
- View all registrations system-wide
- Revenue report with fill rate analytics

---

## Dependencies
- Java 11+
- JavaFX 17+
- MySQL 8.0+
- mysql-connector-j 8.x
