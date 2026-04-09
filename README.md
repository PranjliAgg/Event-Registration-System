# Event Registration System

A comprehensive JavaFX application for event registration with integrated Razorpay payment processing, built as a DBMS college project using MySQL database.

## 🚀 Features

### User Portal
- **Event Browsing**: Browse and search upcoming events by category, date, and venue
- **Smart Registration**: Automatic seat management with waitlist functionality
- **Payment Integration**: Secure Razorpay payment processing with browser-based checkout
- **Registration Management**: View and cancel personal registrations
- **Real-time Updates**: Live seat availability and registration status

### Admin Portal
- **Event Management**: Create and manage events with detailed scheduling
- **Registration Oversight**: View all registrations across the system
- **Revenue Analytics**: Comprehensive revenue reports with fill rate analysis
- **System Monitoring**: Track event performance and user engagement

### Database Features
- **Advanced Triggers**: 7 automated triggers for seat management and audit logging
- **Stored Procedures**: 5 optimized procedures for complex operations
- **Query Optimization**: 24 queries covering basic to advanced SQL concepts
- **Transaction Safety**: ACID-compliant operations with rollback support

## 🛠️ Technology Stack

- **Frontend**: JavaFX 17+ with FXML and CSS styling
- **Backend**: Java 17+ with JDBC connectivity
- **Database**: MySQL 8.0+ with stored procedures and triggers
- **Payment**: Razorpay Java SDK with browser-based checkout
- **Build Tool**: Maven/Gradle compatible

## 📁 Project Structure

```
EventRegistration/
├── database/
│   ├── schema.sql          # Database schema, triggers, procedures, sample data
│   └── queries.sql         # Basic & Complex queries
├── lib/                    # External JAR dependencies
├── src/
│   ├── com/eventregistration/
│   │   ├── MainApp.java                    # JavaFX application entry point
│   │   ├── controller/
│   │   │   ├── LoginController.java        # Authentication logic
│   │   │   ├── UserDashboardController.java # User dashboard & payment
│   │   │   ├── AdminDashboardController.java # Admin management
│   │   │   ├── RegisterController.java     # User registration
│   │   │   └── SceneManager.java           # UI navigation
│   │   ├── dao/
│   │   │   ├── UserDAO.java               # User data operations
│   │   │   ├── EventDAO.java              # Event data operations
│   │   │   └── RegistrationDAO.java       # Registration management
│   │   ├── db/
│   │   │   ├── DatabaseConnection.java    # JDBC connection management
│   │   │   └── DatabaseInitializer.java   # Database setup
│   │   ├── model/
│   │   │   ├── User.java                  # User entity
│   │   │   └── Event.java                 # Event entity
│   │   └── payment/
│   │       └── RazorpayHandler.java       # Payment processing
│   ├── fxml/                              # UI layouts
│   └── css/
│       └── style.css                      # Application styling
└── README.md
```

## ⚙️ Installation & Setup

### Prerequisites
- Java 17 or higher
- MySQL 8.0 or higher
- JavaFX 17+ SDK
- Maven or Gradle (recommended)

### Step 1: Database Setup
1. Install MySQL and create a database user
2. Run the database schema:
```bash
mysql -u root -p < database/schema.sql
```

3. Verify installation:
```sql
USE event_registration_db;
SHOW TABLES;
SELECT * FROM events;
```

### Step 2: Dependencies
Download and place the following JARs in the `lib/` directory:

**Required JARs:**
- `mysql-connector-j-8.x.x.jar` (MySQL Connector/J)
- `javafx-controls.jar`, `javafx-fxml.jar`, `javafx-graphics.jar` (JavaFX)
- `razorpay-java-1.x.x.jar` (Razorpay SDK)
- `okhttp-4.x.x.jar` (HTTP client for Razorpay)
- `commons-text-1.x.x.jar` (String utilities)
- `gson-2.x.x.jar` (JSON processing)

### Step 3: Configuration
1. **Database Credentials**: Update `DatabaseConnection.java`:
```java
private static final String USER = "your_mysql_user";
private static final String PASSWORD = "your_mysql_password";
```

2. **Razorpay Keys**: Create `config.properties` in project root:
```properties
razorpay.key_id=rzp_test_your_key_id
razorpay.key_secret=your_key_secret
```

### Step 4: Build & Run
1. **Using IDE**:
   - Add all JARs from `lib/` to classpath
   - Set VM arguments:
   ```
   --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml
   ```
   - Run `MainApp.java`

2. **Using Maven** (recommended):
   ```xml
   <!-- Add to pom.xml -->
   <dependencies>
       <dependency>
           <groupId>mysql</groupId>
           <artifactId>mysql-connector-java</artifactId>
           <version>8.0.33</version>
       </dependency>
       <dependency>
           <groupId>com.razorpay</groupId>
           <artifactId>razorpay-java</artifactId>
           <version>1.4.6</version>
       </dependency>
       <!-- Add other dependencies -->
   </dependencies>
   ```

## 🔐 Sample Login Credentials

| Role  | Email              | Password |
|-------|--------------------|----------|
| Admin | admin@college.edu  | password |
| User  | alice@student.edu  | password |
| User  | bob@student.edu    | password |

## 💳 Payment Integration

### Razorpay Setup
1. Sign up at [Razorpay Dashboard](https://dashboard.razorpay.com/)
2. Get your API Key ID and Secret
3. Add keys to `config.properties`
4. Test with Razorpay's test mode

### Payment Flow
1. User selects event and payment method
2. System creates Razorpay order via API
3. Browser opens with secure checkout page
4. Payment completion updates registration status
5. Automatic confirmation and seat allocation

## 🗄️ Database Schema

### Core Tables
- `users` - User accounts and profiles
- `events` - Event details and scheduling
- `registrations` - User-event relationships
- `payments` - Payment transactions
- `venues` - Event locations
- `event_categories` - Event classification
- `event_schedule` - Detailed timing
- `audit_log` - System activity tracking

### Triggers (7)
- Seat management automation
- Waitlist promotion
- Duplicate booking prevention
- Deadline enforcement
- Audit logging

### Stored Procedures (5)
- `sp_register_user` - Complete registration flow
- `sp_cancel_registration` - Cancellation with refund
- `sp_event_report` - Analytics and reporting
- `sp_user_registrations` - User history
- `sp_search_events` - Advanced event search

## 📊 Queries Coverage

### Basic Queries
- Simple SELECT operations
- JOIN operations
- Filtering and sorting
- Aggregation functions

### Complex Queries
- Window functions (RANK, DENSE_RANK)
- Advanced subqueries
- Multi-table JOINs
- Running totals and analytics

## 🚀 Usage

### For Users
1. Login with credentials
2. Browse available events
3. Select event and register
4. Complete payment via Razorpay
5. View registration confirmation

### For Admins
1. Login as admin
2. Create new events
3. Monitor registrations
4. Generate revenue reports

## 🔧 Development

### Code Style
- Follow Java naming conventions
- Use meaningful variable names
- Add comments for complex logic
- Handle exceptions appropriately

### Testing
- Test payment flow with Razorpay test keys
- Verify database transactions
- Check UI responsiveness
- Validate form inputs

## 📝 API Documentation

### RazorpayHandler
```java
// Create payment order
Order order = razorpayHandler.createOrder(amount, currency, receipt);

// Handle payment success
void handlePaymentSuccess(String paymentId, String orderId);
```

### DatabaseConnection
```java
// Get connection
Connection conn = DatabaseConnection.getConnection();

// Execute queries safely
PreparedStatement stmt = conn.prepareStatement(sql);
```

## 🤝 Contributing

1. Fork the repository
2. Create feature branch
3. Commit changes
4. Push to branch
5. Create Pull Request

## 📄 License

This project is developed for educational purposes as part of a DBMS college course.

## 🆘 Troubleshooting

### Common Issues
- **JavaFX not found**: Ensure JavaFX SDK is properly added to module path
- **Database connection failed**: Verify MySQL credentials and server status
- **Payment not working**: Check Razorpay API keys and internet connection
- **Build errors**: Ensure all dependencies are in classpath

### Debug Mode
Enable debug logging in `DatabaseConnection.java` for detailed error information.

---

**Note**: This application demonstrates comprehensive database design, JavaFX UI development, and payment gateway integration suitable for real-world event management systems.
