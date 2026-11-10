# Team setup

Use JDK21+, PowerShell7, Node20+ and SQL Server with `sqlcmd`. The setup runner uses Windows integrated SQL authentication; run it as an account allowed to create your own development database. No owner database/backup/private file is required.

```powershell
git clone --branch release/safebox-sprint3-final https://github.com/PhamPhuc0310/Self-Storage-Facility-Rental-and-Management-System.git
cd Self-Storage-Facility-Rental-and-Management-System
pwsh -File database/bootstrap-local.ps1 -Server localhost -TargetDatabase SafeBox_Local
pwsh -File database/apply_10_15.ps1 -Server localhost -TargetDatabase SafeBox_Local -VerifyOnly
```

Bootstrap accepts only `SafeBox_Local` or `SafeBox_Local_<letters/digits>`, creates it if absent, and refuses a nonempty database. On failure it retains the partial local database for inspection; it never drops or resets it. Use another unique local name for a fresh attempt. Never use primary approval/backup flags for team setup. See [database details](database/README.md).

Configure a SQL login permitted to use **your local database** (ask your DBA if needed), then set these variables in the shell that launches Java. Credentials below are prompted, never shared defaults:

```powershell
$env:DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=SafeBox_Local;encrypt=true;trustServerCertificate=true'
$env:DB_USERNAME = Read-Host 'Your local SQL login'
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Your local SQL password' -AsSecureString)).Password
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
cd self-storage
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
npm ci
npm test
.\mvnw.cmd spring-boot:run
```

Open <http://localhost:8080/>. Schema creation is disabled in Hibernate. The application requires all four environment variables and does not automatically load `.env`. Alternatively copy `application-private.properties.example` to the ignored `application-private.properties` and activate `SPRING_PROFILES_ACTIVE=private`. Do not commit local configuration. On Unix, use `bash ./mvnw` for Maven; the documented DB bootstrap requires PowerShell/sqlcmd and integrated SQL access.

Fresh setup includes the five role definitions, with no customers, rentals, payment records, pricing or activated financial policies. Ordinary registration/email verification needs your SMTP capture; optional Mailpit: `docker compose -f compose.mailpit.yml up -d`, inbox <http://localhost:8025/>. Business data should be created through authorized workflows. Existing approved early-return logic supports80% eligible-unused-prepaid-rent refunds only when an appropriate policy has been approved/configured; setup does not activate financial policies or overdue fees.

## Optional five-role local smoke

After the build, stop the app temporarily. Generate a new local password of at least16 characters (keep it in your shell) and create synthetic local accounts. This is optional and runs only on a bootstrapped `SafeBox_Local...` database with no users:

```powershell
$env:SAFEBOX_LOCAL_PASSWORD = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
.\mvnw.cmd --batch-mode --no-transfer-progress dependency:build-classpath '-Dmdep.outputFile=target/local-classpath.txt'
$classpath = (Get-Content target/local-classpath.txt -Raw).Trim()
java --class-path $classpath ../scripts/CreateLocalUsers.java
.\mvnw.cmd spring-boot:run
```

In another shell with the same `SAFEBOX_LOCAL_PASSWORD`, from `self-storage/`:

```powershell
npx playwright install chromium
$env:SAFEBOX_BASE_URL = 'http://127.0.0.1:8080'
npm run test:smoke
node src/test/e2e/role-home-permissions.cjs
```

Accounts: `customer@local.safebox.test`, `staff@local.safebox.test`, `manager@local.safebox.test`, `business@local.safebox.test`, `admin@local.safebox.test`, all using your generated local password. One synthetic facility and bounded staff/reporting assignments are created; no contract, receipt, payment or policy is fabricated. The helper refuses existing users and never resets accounts. Screenshots/results go to ignored `test-results/`. These are local role-access smoke checks, not full seven-flow E2E.

Default Java tests use mocks/H2; real SQL tests are opt-in and may require their own isolated fixtures. Keep `SAFEBOX_SQLSERVER_TEST_*` unset for ordinary `verify`. Owner-specific historical browser campaigns and SQL fixtures are intentionally excluded from the release and remain on the owner's machine.

Prior public history contained a database credential. The owner confirmed rotation/invalidation before this release; history is preserved, and current configuration contains no live credentials. Use fresh local credentials and never reuse historical ones.
