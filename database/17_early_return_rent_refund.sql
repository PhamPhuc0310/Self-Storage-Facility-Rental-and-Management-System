-- Additive only. Default 0 preserves every existing deposit-only policy/settlement.
-- Primary application requires a separate, subsequently approved execution.
:ON ERROR EXIT
SET XACT_ABORT ON;
IF DB_NAME() COLLATE Latin1_General_100_BIN2 NOT IN
 (N'SelfStorageFacilityDB_Test',N'SelfStorageFacilityDB_DemoRehearsal_20261110',N'SelfStorageFacilityDB_DemoVerify_20261110')
 AND NOT (DB_NAME() COLLATE Latin1_General_100_BIN2=N'SelfStorageFacilityDB'
  AND ISNULL(CONVERT(nvarchar(100),SESSION_CONTEXT(N'SafeBoxPrimaryApproval')),N'')=N'REFUND80-20261112')
 THROW 52170,'Migration rehearsal accepts only the explicitly isolated databases; primary requires separately approved script.',1;
BEGIN TRAN;
IF COL_LENGTH('dbo.EARLY_RETURN_POLICIES','rental_refund_percent') IS NULL
 EXEC(N'ALTER TABLE dbo.EARLY_RETURN_POLICIES ADD rental_refund_percent decimal(5,2) NOT NULL CONSTRAINT DF_EARLY_RENT_RATE DEFAULT(0), CONSTRAINT CK_EARLY_RENT_RATE CHECK(rental_refund_percent IN(0,80));');
IF COL_LENGTH('dbo.EARLY_RETURN_SETTLEMENTS','calculation_snapshot') IS NULL
 ALTER TABLE dbo.EARLY_RETURN_SETTLEMENTS ADD calculation_snapshot nvarchar(max) NULL,
 refund_confirmation_reference nvarchar(80) NULL, refund_confirmation_method nvarchar(30) NULL, refund_confirmed_by uniqueidentifier NULL
 REFERENCES dbo.USERS(user_id), refund_confirmed_at datetime2 NULL;
COMMIT;
-- Rollback approach: roll back this transaction on failure. After successful use,
-- keep the additive columns and revert application deployment; never drop audited
-- snapshots. A down migration is permissible only on an unused isolated rehearsal.
