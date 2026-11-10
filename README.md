
<!-- SafeBox Technology Stack -->
![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.4-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![SQL Server](https://img.shields.io/badge/SQL_Server-2022-CC2927?style=flat-square)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=flat-square)
![JWT](https://img.shields.io/badge/Auth-JWT-8A2BE2?style=flat-square)

<!-- Project Information -->
![Architecture](https://img.shields.io/badge/Architecture-REST_API-blue?style=flat-square)
![Sprint](https://img.shields.io/badge/Sprint-3_Completed-2E8B57?style=flat-square)
![Tests](https://img.shields.io/badge/Automated_Tests-310_Passed-228B22?style=flat-square)

<!-- GitHub Actions: replace with the actual workflow file -->
[![Self-Storage-Facility-Rental-and-Management-System (CI included) |  © 2026 by PhamPhuc0310](https://github.com/PhamPhuc0310/Self-Storage-Facility-Rental-and-Management-System/actions/workflows/ci.yml/badge.svg)](https://github.com/PhamPhuc0310/Self-Storage-Facility-Rental-and-Management-System/actions/workflows/ci.yml)


# SafeBox

Self-storage rental and facility management with Java 21, Spring Boot 3.3.4, SQL Server and a plain HTML/CSS/JavaScript frontend served by Spring Boot.

Five roles: Storage Customer, Facility Staff, Facility Manager, Business Operations Manager and System Administrator. Seven flows: reservation, check-in/handover, unit management, fee/revenue management, facility/staff management, renewal/overdue handling and support requests.

## Prerequisites

- JDK 21; use the included Maven Wrapper.
- SQL Server and sqlcmd for database setup. Use your own isolated development instance/database.
- Node.js 20 or newer for JavaScript tests.
- Optional Docker for local SMTP capture with Mailpit.

## Clone

After the approved changes are published, clone the repository and enter its root:

```powershell
git clone https://github.com/PhamPhuc0310/Self-Storage-Facility-Rental-and-Management-System.git
cd Self-Storage-Facility-Rental-and-Management-System
```

For an unmerged pull request, select its published branch before setup. Cloning does not copy anyone else's database, environment variables, private configuration or local test evidence. Follow the database and environment steps below before starting the application.

<!-- SafeBox Technology Stack -->
![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.4-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![SQL Server](https://img.shields.io/badge/SQL_Server-2022-CC2927?style=flat-square)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=flat-square)
![JWT](https://img.shields.io/badge/Auth-JWT-8A2BE2?style=flat-square)

<!-- Project Information -->
![Architecture](https://img.shields.io/badge/Architecture-REST_API-blue?style=flat-square)
![Tests](https://img.shields.io/badge/Automated_Tests-_Passed-228B22?style=flat-square)

<!-- GitHub Actions: replace with the actual workflow file -->
[![Self-Storage-Facility-Rental-and-Management-System (CI included) |  © 2026 by PhamPhuc0310](https://github.com/PhamPhuc0310/Self-Storage-Facility-Rental-and-Management-System/actions/workflows/ci.yml/badge.svg)](https://github.com/PhamPhuc0310/Self-Storage-Facility-Rental-and-Management-System/actions/workflows/ci.yml)


# SafeBox

Self-storage rental and facility management with Java 21, Spring Boot 3.3.4, SQL Server and a plain HTML/CSS/JavaScript frontend served by Spring Boot.

Five roles: Storage Customer, Facility Staff, Facility Manager, Business Operations Manager and System Administrator. Seven flows: reservation, check-in/handover, unit management, fee/revenue management, facility/staff management, renewal/overdue handling and support requests.

## Run locally

Follow [TEAM_SETUP.md](TEAM_SETUP.md) for the verified clean-database bootstrap, environment configuration, build and five-role smoke procedure. Setup uses your own SQL Server and credentials; it does not require the owner's database, backup or private files.

- JDK21+, Maven Wrapper, Node20+, PowerShell7 and SQL Server/sqlcmd.
- Initialize an empty `SafeBox_Local...` database using `database/bootstrap-local.ps1`, then verify migrations10–17 using the included ledger runner. [Database details](database/README.md).
- Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and a random `JWT_SECRET`. No credentials are supplied by the repository and `.env` is not automatically loaded.
- From `self-storage/`: `./mvnw.cmd clean verify`, `npm ci`, `npm test`, then `./mvnw.cmd spring-boot:run`. Unix uses `bash ./mvnw`.
- Open <http://localhost:8080/>. Registration/email verification needs local SMTP; optional Mailpit instructions are in the setup guide.

The release includes acceptance/ownership guards, authorized payment/handover and return lifecycle checks, renewal/overdue governance, scoped reporting/support interfaces and the approved80% eligible-unused-prepaid-rent early-return calculation. A refund still requires an effective approved policy and the existing inspection/settlement/evidence workflow. Setup does not activate financial policies or seed customer/payment history.

## Tests and team workflow

Java regression tests use mocks/H2 by default. Real SQL tests are opt-in against isolated databases and can require specific fixtures. JavaScript tests run with `npm test`. The portable Playwright smoke covers authorized Customer, Staff, Manager, Business Manager and Admin routes; it is not a seven-flow write-based E2E certification. See [TEAM_SETUP.md](TEAM_SETUP.md).

See [CONTRIBUTING.md](CONTRIBUTING.md) for review workflow. Preserve Maven wrapper resources, canonical migrations and regression tests. Local environments, backups, generated outputs, historical audit reports and owner-specific campaign fixtures remain ignored. The owner confirmed that the formerly exposed credential was rotated/invalidated; current configuration requires private environment credentials. Git history is preserved.

## Contact
Connect me via: phamhoangphuccpag@gmail.com
© 2026 - Tí Thì Rớt Môn
