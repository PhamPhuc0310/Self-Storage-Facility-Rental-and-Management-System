# Sprint 3 backend: UC19 and UC24

## Architecture and dependencies

The existing `RENTAL_CONTRACTS`, `TERMINATION_REQUESTS`, `HANDOVER_RECORDS`, `INSPECTION_REPORTS`, `INVOICES`, `PAYMENT_TRANSACTIONS`, `DEPOSIT_REFUNDS`, `STORAGE_UNITS`, and `USER_FACILITY_ASSIGNMENTS` tables are reused. UC16 owns return appointments, handover, inspection, contract `RETURNED`, and unit release. UC11/UC12 own the existing payment and pickup behavior. UC18 has no implemented charge service; only overdue invoice balances are reported. There was no existing reporting API. The `RENTAL_POLICIES.return_policy` value is free text and cannot safely determine a numeric settlement.

## UC19: early return

| Method/path | Role | Request | Response and transition |
|---|---|---|---|
| `POST /api/customer/contracts/{contractId}/early-return` | Customer who owns the ACTIVE contract | `{"proposedReturnAt":"2026-10-25T10:00:00","reason":"Moving","notes":"Optional"}` | 201, termination request `REQUESTED` |
| `GET /api/customer/early-returns` | Customer | None | Own request history |
| `GET /api/customer/early-returns/{id}` | Customer | None | Own request |
| `GET /api/manager/early-returns` | Assigned Facility Manager | None | Requests in assigned facilities |
| `GET /api/manager/early-returns/{id}/settlement` | Assigned Facility Manager | None | `PENDING_POLICY`, `POLICY_CONFLICT`, `PENDING_INSPECTION`, `READY_FOR_APPROVAL`, or persisted settlement |
| `POST /api/manager/early-returns/{id}/settlement/approve` | Assigned Facility Manager | `{"inspectionDeduction":100000,"deductionReason":"Verified repair"}` | Settlement snapshot; request `SETTLEMENT_APPROVED` |
| `POST /api/manager/early-returns/{id}/settlement/reject` | Assigned Facility Manager | `{"reason":"Reason"}` | Request `REJECTED` |
| `POST /api/manager/early-returns/{id}/refund` | Assigned Facility Manager | Empty body | One simulated refund transaction, completed deposit refund, request `REFUNDED`; retry returns the same settlement |

Errors use the existing `BusinessException` handler: 400 for invalid input/date, 403 for out-of-scope resources, 404 for missing requests, and 409 for lifecycle, policy, inspection, payment, or balance conflicts. JWT identity supplies the customer and manager IDs. No client supplied amount or facility is trusted. A request is valid only before the contract end date. The UC16 schedule endpoint accepts that same proposed time after a request exists; all UC16 operating-hours and staff assignment checks still run. UC16 alone records physical return and inspection. Refund processing never changes the contract, unit, or appointment.

Exactly one versioned `EARLY_RETURN_POLICIES` row must be approved and entered for the facility and proposed date before settlement approval. No row is seeded. Overlapping active policies produce `POLICY_CONFLICT`. The implemented policy model supports explicit deposit refund permission and inspection deduction permission. Unused rent refund is zero **only under this explicit deposit-only policy**; other rental refund modes are pending business policy and are not implemented. The deduction must have a reason and cannot exceed the inspection repair estimate. Open nondeposit invoices block approval. The snapshot stores original amounts, collected deposit and rent, prior refunds, open receivables, deduction, policy/version, handover and inspection IDs, calculation time, manager, and final amounts.

Refund uses a pessimistic lock on the termination request and contract, checks the remaining confirmed deposit again, inserts one `PAYMENT_TRANSACTIONS` row with `REFUND`/`DEPOSIT`/`SUCCESS`, and inserts a completed `DEPOSIT_REFUNDS` row in the same database transaction. The unique settlement request, active request, and refund reference indexes are database backstops. A retry after commit returns the existing settlement and does not insert a new transaction. The existing successful payment queries exclude refund transactions so UC11 deposit balances remain correct.

An open legacy deposit refund request blocks starting UC19. While a UC19 request is active, the legacy deposit refund request/review/complete path rejects that contract so it cannot bypass the inspection and settlement gates. Other contracts continue through the existing flow.

## UC24: reporting

All endpoints require Facility Manager or Business Operations Manager and `startDate`, `endDate` query parameters in `YYYY-MM-DD`; `facilityId` is optional. `GET /api/reports/revenue-trend` also accepts `grouping=DAY|MONTH`. Both roles are limited to **active assigned facilities**. No assignments produce zeros or empty lists. An unauthorized facility ID returns 403; an invalid or longer than 367-day inclusive range returns 400.

| Endpoint | Response |
|---|---|
| `GET /api/reports/financial-summary` | Confirmed rental, deposit, other, and unclassified collections; refund outflow; net cash movement; nondeposit outstanding and overdue receivables |
| `GET /api/reports/revenue-trend` | Dated collection and refund series |
| `GET /api/reports/facility-performance` | Current unit status counts, occupancy, active contracts, confirmed collections, outstanding receivables per authorized facility |
| `GET /api/reports/operational-summary` | Current active/expiring/overdue contracts and pending returns, completed returns during the period, current maintenance units |

The reporting zone is `Asia/Ho_Chi_Minh`. Stored SQL Server `DATETIME2` values are treated as local times in that zone. Start date is inclusive at 00:00; end date is exclusive at 00:00 on the following day. Collections use `PAYMENT_TRANSACTIONS.transaction_date` with `SUCCESS` only. Failed and pending rows are excluded. Successful retries sharing a nonnull reference, transaction type, and payment type are counted once. Deposit receipts are separate from rental collections. Unknown legacy payment types remain unclassified rather than guessed. Refund outflow combines successful refund transactions and older completed `DEPOSIT_REFUNDS` records without a matching successful refund transaction, avoiding double counting the new UC19 refund. Receivables are the open balance of nondeposit invoices issued by the end boundary; overdue means `due_date` before the end boundary. These are operational balances, not an accounting ledger. UC18 charge totals are omitted because no reliable UC18 charge records exist.

Unit and contract counts are current snapshots; completed returns use `actual_at` in the selected period. Occupancy rate is `OCCUPIED / (all units - MAINTENANCE) * 100`, zero when no rentable units exist. Reporting uses separate grouped queries for payments, invoices, units, contracts, and returns so joins do not multiply totals; it does not fetch customer information.

## Migration and deployment

The local `SelfStorageFacilityDB` on SQL Server 2025 Express was backed up on 2026-10-23 before migration. The copy-only, checksummed backup is `C:\Program Files\Microsoft SQL Server\MSSQL17.SQLEXPRESS\MSSQL\Backup\SelfStorageFacilityDB_UC19_UC24_20261023_1142.bak`; `RESTORE VERIFYONLY ... WITH CHECKSUM` reported it valid. The initial transactional attempt stopped at a filtered-index `QUOTED_IDENTIFIER` error. A query confirmed that both new tables and all three indexes had rolled back. After setting SQL Server's required indexed-object session options, the transaction in [`database/08_uc19_uc24_apply.sql`](../database/08_uc19_uc24_apply.sql) committed the additive [`database/07_uc19_uc24_additive.sql`](../database/07_uc19_uc24_additive.sql). Post-checks confirmed two new tables, three indexes, zero policy/settlement rows, and unchanged counts in all seven checked pre-existing business tables. Hibernate schema updates remain disabled.

For another existing database, take and verify a full database backup first, then run the wrapper from the repository root with SQLCMD integrated authentication and the application's trusted-certificate setting:

```powershell
sqlcmd -S tcp:127.0.0.1,1433 -d SelfStorageFacilityDB -E -C -b -i database\08_uc19_uc24_apply.sql
```

For a new database, apply `database/SelfStorageFacilityDB.sql`, then `database/UC11_UC12_Additive.sql`, then the wrapper. The wrapper checks prerequisites and duplicate keys, applies the migration in one transaction, and checks postconditions. No pre-existing table or row is removed. Preserve the backup; dropping new indexes or tables is not a financial rollback. Never reverse a recorded refund by deleting its transaction.

### Policy activation

The existing active `RENTAL_POLICIES.return_policy` states only that handover and inspection precede closure; it specifies no monetary rule. The new `EARLY_RETURN_POLICIES` table requires an existing `facility_id`, a distinct `version`, `effective_from`, optional `effective_to`, explicit `deposit_refundable` and `inspection_deduction_allowed` bits, and `status='ACTIVE'`. Exactly one active row may cover a proposed return date. The checked-in [`database/09_uc19_policy_activation_template.sql`](../database/09_uc19_policy_activation_template.sql) starts with `@Approved=0` and null required values, so executing it as-is writes nothing. After business approval, fill every variable, change `@Approved` to 1, review the overlap check, and execute it against `SelfStorageFacilityDB` with `sqlcmd -E -C -b -i database\09_uc19_policy_activation_template.sql`. No policy was activated during this implementation. This deposit-only model does not decide unused-rent refunds or penalties.

Frontend integration can call the new APIs; no frontend files were changed. The existing UC11 payment and UC12 appointment endpoints remain available.

## Completion status

- **UC19: PARTIALLY COMPLETE.** Request, tracking, inspection-gated deposit-only settlement, approval, and idempotent simulated refund are implemented. The schema, mappings, and rollback-only request persistence were verified on SQL Server; the database had zero termination requests after the test. Live approval/refund and concurrency verification await an approved facility policy; unused-rent refunds and penalties require separately approved business rules.
- **UC24: PARTIALLY COMPLETE.** Financial, trend, facility, and operational endpoints were exercised against persisted SQL Server records through read-only Spring HTTP integration tests. UC18 charge detail is unavailable and was not fabricated. The management report is not a historical accounting ledger.

## Files changed

| File | Purpose |
|---|---|
| `database/07_uc19_uc24_additive.sql` | Policy/settlement tables, uniqueness and reporting indexes |
| `database/08_uc19_uc24_apply.sql` | Transactional SQLCMD migration wrapper with preconditions and postconditions |
| `database/09_uc19_policy_activation_template.sql` | Manual approval-gated policy configuration template |
| `self-storage/src/main/java/com/safebox/self_storage/entity/TerminationRequest.java` | Existing termination table mapping |
| `self-storage/src/main/java/com/safebox/self_storage/entity/EarlyReturnPolicy.java` | Versioned financial policy mapping |
| `self-storage/src/main/java/com/safebox/self_storage/entity/EarlyReturnSettlement.java` | Approved financial snapshot mapping |
| `self-storage/src/main/java/com/safebox/self_storage/repository/TerminationRequestRepository.java` | Request lookup, facility scope, lock |
| `self-storage/src/main/java/com/safebox/self_storage/repository/EarlyReturnPolicyRepository.java` | Effective policy lookup |
| `self-storage/src/main/java/com/safebox/self_storage/repository/EarlyReturnSettlementRepository.java` | Settlement lookup |
| `self-storage/src/main/java/com/safebox/self_storage/repository/PaymentTransactionRepository.java` | Exclude refunds from confirmed receipt queries |
| `self-storage/src/main/java/com/safebox/self_storage/service/DepositRefundService.java` | Block legacy refund path for active UC19 contracts |
| `self-storage/src/main/java/com/safebox/self_storage/service/EarlyReturnService.java` | UC19 validation, settlement, refund |
| `self-storage/src/main/java/com/safebox/self_storage/service/ReportingService.java` | UC24 database queries and metric calculations |
| `self-storage/src/main/java/com/safebox/self_storage/service/impl/ReturnInspectionServiceImpl.java` | Allow UC16 appointment for an existing early request |
| `self-storage/src/main/java/com/safebox/self_storage/controller/EarlyReturnController.java` | UC19 REST endpoints |
| `self-storage/src/main/java/com/safebox/self_storage/controller/ReportingController.java` | UC24 REST endpoints |
| `self-storage/src/main/java/com/safebox/self_storage/config/SecurityConfig.java` | Endpoint role rules |
| `self-storage/src/test/java/com/safebox/self_storage/service/EarlyReturnServiceTest.java` | UC19 service tests |
| `self-storage/src/test/java/com/safebox/self_storage/service/DepositRefundServiceTest.java` | Legacy refund guard test |
| `self-storage/src/test/java/com/safebox/self_storage/service/ReportingServiceTest.java` | UC24 H2 query tests |
| `self-storage/src/test/java/com/safebox/self_storage/service/ReturnInspectionServiceTest.java` | UC16 test fixture update |
| `self-storage/src/test/java/com/safebox/self_storage/service/Sprint3SqlServerIntegrationTest.java` | Read-only live SQL Server schema and four-endpoint tests |
| `self-storage/src/test/java/com/safebox/self_storage/service/EarlyReturnSqlServerIntegrationTest.java` | Rollback-only real SQL Server UC19 request persistence test |
| `docs/sprint3-uc19-uc24-backend.md` | Integration and API documentation |

## Verification

`mvn clean test` and `mvn clean package` both passed with the normal database-independent suite: 164 tests reported, 0 failures, 0 errors, 6 opt-in SQL Server tests skipped. The two new opt-in SQL Server suites were then run separately and passed 4/4 tests against the local database. The rollback-only UC19 test persisted and read an owned early-return request, rejected a duplicate, and confirmed that the contract and appointment remained unchanged; a separate SQL query confirmed the request rolled back. The three UC24/schema tests checked JPA mappings and indexes, compared the financial summary with persisted confirmed rental/deposit payments, reconciled daily trend with summary, checked current unit and active-contract counts, called all four HTTP report endpoints, and rejected an unauthorized facility ID. The two existing reservation-approval SQL Server tests require a disposable database and a specific pending reservation; they were not run on this populated database. The Windows/JDK 26 Maven JVM needed Mockito preloaded with `JAVA_TOOL_OPTIONS=-javaagent:<local mockito-core 5.14.0 jar>` and `-DforkCount=0`. No live UC19 refund was executed because no approved policy row exists.
