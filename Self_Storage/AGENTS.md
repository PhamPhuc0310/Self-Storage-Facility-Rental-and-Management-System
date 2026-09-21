# SafeBox Storage - Development Rules

## Project
Self-Storage Facility Rental and Management System.

Backend stack:
- Java 21
- Spring Boot
- Maven
- Spring Web
- Spring Data JPA
- Spring Security
- SQL Server
- REST API

Database:
- Database name: SelfStorageFacilityDB
- Schema file: ../database/SelfStorageFacilityDB.sql
## Rules

1. Do not modify the database schema unless explicitly requested.
2. Do not rename existing tables or columns.
3. JPA entities must match the existing SQL Server schema.
4. SQL Server UNIQUEIDENTIFIER must map to Java UUID.
5. Do not replace UUID IDs with Long.
6. Keep spring.jpa.hibernate.ddl-auto=none.
7. Do not let Hibernate create or modify tables.

8. Architecture:
   Controller
   -> Service
   -> Repository
   -> Database

9. Packages:
   com.safebox.storage.controller
   com.safebox.storage.service
   com.safebox.storage.service.impl
   com.safebox.storage.repository
   com.safebox.storage.entity
   com.safebox.storage.dto.request
   com.safebox.storage.dto.response
   com.safebox.storage.security
   com.safebox.storage.config
   com.safebox.storage.exception

10. Do not put database logic inside controllers.
11. Do not expose JPA entities directly in API responses.
12. Use DTOs.
13. Passwords must use BCrypt.
14. Authentication should use JWT.
15. User status must be checked during authentication.

Roles:
- CUSTOMER
- FACILITY_STAFF
- FACILITY_MANAGER
- BUSINESS_OPERATIONS_MANAGER
- SYSTEM_ADMIN

16. Do not implement unrelated features unless requested.

17. Before finishing a task:
- compile the project
- run tests when available
- check compilation errors
- summarize changed files

## Current Task
Implement backend login authentication only.

Do not implement:
- registration
- forgot password
- payment
- contract
- reservation
- unrelated features