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

## Database setup

Use your own isolated development SQL Server instance. Schema initialization is manual: Hibernate uses ddl-auto=none. The [ERD source](database/ERD.drawio) documents the database. Only reviewed SQL files and this ERD are uploaded under database/; local Markdown and PowerShell helpers are not part of a clone.

1. Review database/SelfStorageFacilityDB.sql, then apply it only to a new development database. It creates/uses the literal SelfStorageFacilityDB name. Some later scripts also hardcode that name: selecting a different target with sqlcmd does not override USE. For a different name, prepare reviewed local execution copies; preserve the shared SQL originals. Never target an existing shared/primary database for setup.
2. After the baseline, review/apply 03_auth_email_migration.sql and UC11_UC12_Additive.sql; then UC17_Additive.sql, UC18_Additive.sql, 06_uc22_support_requests_compatibility.sql, 07_uc22_uc23_support.sql, sprint3_uc20_migration.sql and sprint3_uc21_migration.sql against their prerequisites. Authentication and UC18 hardcode the original target.
3. Apply UC19/UC24 using 08_uc19_uc24_apply.sql from the repository root; its :r includes database/07_uc19_uc24_additive.sql. Do not apply that included file a second time as an extra required step.
4. With prerequisites present, review/apply scripts 10 through 16 in order using sqlcmd SQLCMD mode, -b and an explicitly verified development target. Each requires SafeBoxTargetDb set to your development name and SafeBoxAllowPrimary=NO. Reconfirm SELECT DB_NAME() before execution and stop on any error. The local PowerShell ledger runner is excluded from Git; direct SQL execution does not supply its ledger/checksum checks, so record applied versions separately.

Example syntax for one reviewed migration on your development database (Windows integrated authentication):

```powershell
sqlcmd -S localhost -E -C -b -d SafeBox_Local -v SafeBoxTargetDb=SafeBox_Local SafeBoxAllowPrimary=NO -i database/10_sprint3_remediation.sql
```

This is a prerequisite review guide, not a verified one-command empty-database installer. This cleanup does not execute SQL. Preserve script contents; review all legacy hardcoded targets and SQL errors in isolation. Optional source-listing/catalog/account/contract seeds are demo fixtures, not production initialization; inspect their identifiers, test hashes and targets before use. Scripts under database/demo/ are optional isolated campaign fixtures, not required schema migrations. The rehearsal scripts require their specifically named disposable/test databases; setup/reset placeholders intentionally stop until a reviewed fixture manifest exists. Do not include them in routine database initialization. Policy activation requires an authorized business decision, not invented rates. A runnable demo also needs appropriate role users/assignments, verified/priced units and approved policies.

## Local configuration

Supply these environment variables in the shell or IDE that launches Java:

| Variable | Value |
|---|---|
| DB_URL | Your JDBC URL, e.g. jdbc:sqlserver://localhost:1433;databaseName=SafeBox_Local;encrypt=true;trustServerCertificate=true |
| DB_USERNAME | Your development SQL login |
| DB_PASSWORD | Your development SQL password |
| JWT_SECRET | A locally generated random secret of at least 32 UTF-8 bytes |

PowerShell 7 example (prompts avoid storing credentials in this README or shell command history):

```powershell
$env:DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=SafeBox_Local;encrypt=true;trustServerCertificate=true'
$env:DB_USERNAME = Read-Host 'Development SQL login'
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Development SQL password' -AsSecureString)).Password
$env:JWT_SECRET = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

On other platforms, export the same variables in your shell. The application does **not** automatically load .env files. Always set DB_URL explicitly: the legacy URL fallback still names SelfStorageFacilityDB. Never commit live credentials, account QR images or database backups.

An optional [private profile example](self-storage/src/main/resources/application-private.properties.example) can be copied to application-private.properties in the same folder and activated with SPRING_PROFILES_ACTIVE=private. The real file is ignored; keep its credentials private. Normal environment-variable configuration needs no profile.

## Build, test and run

From the repository root, using PowerShell:

```powershell
cd self-storage
.\mvnw.cmd --batch-mode --no-transfer-progress verify
node --test src/test/js/*.test.js
.\mvnw.cmd spring-boot:run
```

On Unix, replace .\mvnw.cmd with bash ./mvnw. Open [localhost:8080](http://localhost:8080/); backend and frontend share this process. Configure the database before startup. Registration/email verification requires SMTP; optional local capture is docker compose -f compose.mailpit.yml up -d, with the inbox at [localhost:8025](http://localhost:8025/). Existing SMTP defaults use localhost:1025. Never use demo fixture identities or passwords for a live deployment.

The default Java suite uses mocks and H2. SQL Server tests are opt-in through SAFEBOX_SQLSERVER_TEST_URL, SAFEBOX_SQLSERVER_TEST_USER and SAFEBOX_SQLSERVER_TEST_PASSWORD; inspect each test's database-name and fixture requirements before enabling them. They can mutate data. Leave the opt-in URL unset for ordinary unit-test/CI runs.

Browser tests under src/test/e2e are manual campaigns requiring Playwright, Chromium, sometimes bcryptjs, sqlcmd, explicit environment variables and campaign-specific database fixtures. Several are Windows-specific and read local .env.codex.local or prior result JSON. Prior result JSON inputs under docs/testing/evidence are local-only under the required docs policy; obtain or generate the matching campaign inputs locally before running continuation scripts. They are not available in a fresh clone and do not recreate database state. These scripts are not a portable one-command fresh-clone test suite. Do not run primary-named scripts on a shared database.

## Team workflow and publication

See [CONTRIBUTING.md](CONTRIBUTING.md). [CI](.github/workflows/ci.yml) builds/tests/packages with Java 21 and Maven; JavaScript tests are run separately with the command above. Keep migrations and all role interfaces with the source. Only docs/sprint.xlsx and docs/stitch_safebox_storage_rental_platform.zip are tracked under docs/. Historical reports, screenshots, evidence and personal tooling remain local and ignored.

**Publication blocked:** a database credential existed in Git history. The current configuration now requires environment credentials, but this does not erase past commits. The owner must rotate the exposed credential wherever used and complete a separately reviewed history-remediation/publication decision before publishing. No rotation or history rewrite is automated here.

## Contact
Connect me via: phamhoangphuccpag@gmail.com
© 2026 - Tí Thì Rớt Môn
