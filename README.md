# SafeBox Storage

> Self-Storage Facility Rental and Management System

SafeBox Storage is a web-based system designed to support the rental and management of self-storage facilities. The system provides a centralized platform for customers, facility staff, managers, and administrators to manage storage rental operations efficiently.

This project is developed as part of the **Software Project (SWP)** course.

---

## Overview

SafeBox Storage aims to digitalize the main operations of a self-storage business, including:

- Searching for storage facilities and storage units
- Reserving storage units
- Customer check-in and unit handover
- Managing active rentals
- Contract and renewal management
- Payment and additional fee handling
- Storage unit and facility management
- Support request management
- Role-based system administration

---

## User Roles

The system supports five main roles:

| Role | Description |
|---|---|
| `CUSTOMER` | Searches, rents, and manages storage units |
| `FACILITY_STAFF` | Handles check-in, handover, return, and on-site operations |
| `FACILITY_MANAGER` | Manages storage units and facility operations |
| `BUSINESS_OPERATIONS_MANAGER` | Monitors business rules, fees, and operations |
| `SYSTEM_ADMIN` | Manages system-level administration |

---

## Technology Stack

### Backend

- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA
- Spring Security
- Bean Validation
- JWT Authentication
- BCrypt Password Hashing
- Maven

### Database

- Microsoft SQL Server
- Microsoft JDBC Driver for SQL Server

### Frontend

- HTML5
- CSS3
- JavaScript
- Responsive Web Interface

### Development Tools

- Apache NetBeans
- SQL Server Management Studio
- Git
- GitHub

---

## Project Structure

```text
self-storage/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/safebox/self_storage/
│   │   │       ├── controller/
│   │   │       ├── dto/
│   │   │       ├── entity/
│   │   │       ├── exception/
│   │   │       ├── repository/
│   │   │       ├── security/
│   │   │       └── service/
│   │   │
│   │   └── resources/
│   │       ├── static/
│   │       └── application.properties
│   │
│   └── test/
│
├── .gitignore
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
```

---

## Main System Flows

The system is designed around the following main business flows:

1. Storage Unit Reservation
2. Storage Check-in and Handover
3. Rented Storage Unit Management
4. Business Rules, Fee Management, and Revenue Monitoring
5. Facility Storage and Staff Management
6. Storage Renewal and Overdue Handling
7. Support Request and Issue Handling

---

## Authentication & Authorization

SafeBox Storage uses JWT-based authentication and role-based authorization.

The authentication flow follows this general process:

```text
User
  │
  ▼
Login
  │
  ▼
Validate Credentials
  │
  ▼
Check Account Status
  │
  ▼
Generate JWT
  │
  ▼
Return User Information + Token
  │
  ▼
Redirect Based on Role
```

Security mechanisms include:

- BCrypt password hashing
- JWT authentication
- Role-based authorization
- Account status validation
- Protected API endpoints
- `401 Unauthorized` handling
- `403 Forbidden` handling
- Environment-based secret configuration

---

## Getting Started

### Prerequisites

Make sure the following software is available:

- JDK 21
- Microsoft SQL Server
- Git

Maven does not need to be installed globally because the project includes **Maven Wrapper**.

---

## Database Configuration

The application uses Microsoft SQL Server.

Default development configuration:

```text
Server: localhost
Port: 1433
Database: SelfStorageFacilityDB
```

Hibernate is configured not to automatically modify the database schema:

```properties
spring.jpa.hibernate.ddl-auto=none
```

The database schema and required seed data must be prepared before running the application.

---

## Environment Variables

Sensitive information should not be stored directly in the source code.

Configure the following environment variables before starting the application:

```text
DB_USERNAME=your_database_username
DB_PASSWORD=your_database_password
JWT_SECRET=your_jwt_secret
```

Never commit real database credentials, JWT secrets, `.env` files, or other sensitive configuration to the repository.

---

## Running the Application

### NetBeans

Open the project in Apache NetBeans and run:

```text
SafeStorageApplication.java
```

or use:

```text
Run Project (F6)
```

After the application starts successfully, access:

```text
http://localhost:8080/
```

### Command Line

Windows:

```bash
mvnw.cmd spring-boot:run
```

macOS/Linux:

```bash
./mvnw spring-boot:run
```

---

## Running Tests

Windows:

```bash
mvnw.cmd clean test
```

macOS/Linux:

```bash
./mvnw clean test
```

---

## Development Workflow

The project uses Git for version control and follows a branch-based development workflow.

```text
main
  │
  └── develop
        │
        ├── feature/...
        ├── feature/...
        └── fix/...
```

New features should be developed in separate branches and integrated through Pull Requests.

Direct development on the `main` branch should be avoided.

---

## Team

Developed by the **SafeBox Storage Development Team**

Software Engineering  
FPT University

---

## License

This project is developed for educational purposes as part of the Software Project (SWP) course.