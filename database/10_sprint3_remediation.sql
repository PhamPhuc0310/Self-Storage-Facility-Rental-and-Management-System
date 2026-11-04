:ON ERROR EXIT
/* SafeBox Sprint 3 remediation. Apply after the UC18, UC21 and UC22 migrations
   to an isolated, backed-up SQL Server database. Idempotent and additive. */
SET XACT_ABORT ON;
IF DB_NAME() <> N'$(SafeBoxTargetDb)' THROW 51010, 'Explicit SafeBox target database does not match the current database.', 1;
IF DB_NAME() = N'SelfStorageFacilityDB' AND N'$(SafeBoxAllowPrimary)' <> N'YES' THROW 51011, 'Primary database requires explicit reviewed deployment authorization.', 1;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.RENTAL_POLICIES', N'U') IS NULL
   OR OBJECT_ID(N'dbo.SUPPORT_REQUESTS', N'U') IS NULL
   OR OBJECT_ID(N'dbo.SUPPORT_REQUEST_MESSAGES', N'U') IS NULL
   OR OBJECT_ID(N'dbo.SUPPORT_TICKETS', N'U') IS NULL
   OR OBJECT_ID(N'dbo.SUPPORT_TICKET_REPLIES', N'U') IS NULL
    THROW 51018, 'Apply the base, UC18, UC21 and UC22 schemas before remediation.', 1;

/* An old default rate is not evidence of financial approval. Existing rows stay
   unapproved until an authorized, separately documented approval is recorded. */
IF COL_LENGTH('dbo.RENTAL_POLICIES', 'overdue_approved_at') IS NULL
    ALTER TABLE dbo.RENTAL_POLICIES ADD overdue_approved_at DATETIME2 NULL;
IF COL_LENGTH('dbo.RENTAL_POLICIES', 'overdue_approved_by') IS NULL
    ALTER TABLE dbo.RENTAL_POLICIES ADD overdue_approved_by UNIQUEIDENTIFIER NULL;
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_RENTAL_POLICIES_OVERDUE_APPROVER')
    ALTER TABLE dbo.RENTAL_POLICIES ADD CONSTRAINT FK_RENTAL_POLICIES_OVERDUE_APPROVER
        FOREIGN KEY (overdue_approved_by) REFERENCES dbo.USERS(user_id);

/* UC21's ticket is authoritative. Retain the old request/message tables as a
   read-only historical source; copy their IDs so links and reply order survive. */
ALTER TABLE dbo.SUPPORT_TICKETS ALTER COLUMN title NVARCHAR(255) NOT NULL;

IF EXISTS (
    SELECT 1 FROM dbo.SUPPORT_REQUESTS r
    JOIN dbo.SUPPORT_TICKETS t ON t.ticket_id = r.request_id
    WHERE t.contract_id <> r.contract_id OR t.customer_id <> r.requested_by
)
    THROW 51019, 'Support ticket ID collision with different owner/contract.', 1;

IF EXISTS (
    SELECT 1 FROM dbo.SUPPORT_REQUESTS r
    JOIN dbo.RENTAL_CONTRACTS c ON c.contract_id = r.contract_id
    LEFT JOIN dbo.STORAGE_UNITS u ON u.unit_id = c.unit_id
    WHERE u.facility_id IS NULL
)
    THROW 51020, 'Legacy support request has no resolvable facility.', 1;

INSERT INTO dbo.SUPPORT_TICKETS
    (ticket_id, ticket_number, contract_id, customer_id, facility_id, issue_type,
     title, description, status, priority, assigned_to, resolved_at,
     resolution_note, created_at, updated_at)
SELECT r.request_id, CONCAT('SR-', CONVERT(varchar(36), r.request_id)), r.contract_id,
       r.requested_by, u.facility_id,
       CASE WHEN r.request_type = 'ACCESS' THEN 'ACCESS_ISSUE' ELSE r.request_type END,
       r.title, r.description, r.status, r.priority, r.assigned_staff_id,
       r.resolved_at, r.resolution_note, r.created_at,
       COALESCE(r.resolved_at, r.created_at)
FROM dbo.SUPPORT_REQUESTS r
JOIN dbo.RENTAL_CONTRACTS c ON c.contract_id = r.contract_id
JOIN dbo.STORAGE_UNITS u ON u.unit_id = c.unit_id
WHERE NOT EXISTS (SELECT 1 FROM dbo.SUPPORT_TICKETS t WHERE t.ticket_id = r.request_id);

IF EXISTS (
    SELECT 1 FROM dbo.SUPPORT_REQUEST_MESSAGES m
    JOIN dbo.SUPPORT_TICKET_REPLIES p ON p.reply_id = m.message_id
    WHERE p.ticket_id <> m.request_id OR p.user_id <> m.author_id
)
    THROW 51021, 'Support reply ID collision with different ticket/author.', 1;

INSERT INTO dbo.SUPPORT_TICKET_REPLIES
    (reply_id, ticket_id, user_id, user_role, user_full_name, content, created_at)
SELECT m.message_id, m.request_id, m.author_id, ro.role_name, u.full_name,
       m.message, m.created_at
FROM dbo.SUPPORT_REQUEST_MESSAGES m
JOIN dbo.USERS u ON u.user_id = m.author_id
JOIN dbo.ROLES ro ON ro.role_id = u.role_id
WHERE NOT EXISTS (SELECT 1 FROM dbo.SUPPORT_TICKET_REPLIES p WHERE p.reply_id = m.message_id);

IF EXISTS (SELECT 1 FROM dbo.SUPPORT_REQUESTS r
           WHERE NOT EXISTS (SELECT 1 FROM dbo.SUPPORT_TICKETS t WHERE t.ticket_id = r.request_id))
   OR EXISTS (SELECT 1 FROM dbo.SUPPORT_REQUEST_MESSAGES m
              WHERE NOT EXISTS (SELECT 1 FROM dbo.SUPPORT_TICKET_REPLIES p WHERE p.reply_id = m.message_id))
    THROW 51022, 'Support migration postcondition failed.', 1;

COMMIT TRANSACTION;
