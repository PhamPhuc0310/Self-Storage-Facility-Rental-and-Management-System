:ON ERROR EXIT
-- Destructive only inside the named rehearsal restore; exact synthetic IDs only.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
IF DB_NAME() <> N'SafeBox_FinalRehearsal_20261030' THROW 51980,'Rehearsal DB required',1;
DECLARE @ids TABLE(contract_id uniqueidentifier PRIMARY KEY,reservation_id uniqueidentifier,unit_id uniqueidentifier,contract_number nvarchar(50));
INSERT @ids VALUES
('00000000-0000-4000-8000-00000000D401','00000000-0000-4000-8000-00000000D301','00000000-0000-4000-8000-00000000D201',N'DEMO-RENEW-01'),
('00000000-0000-4000-8000-00000000D402','00000000-0000-4000-8000-00000000D302','00000000-0000-4000-8000-00000000D202',N'DEMO-OVERDUE-01'),
('00000000-0000-4000-8000-00000000D403','00000000-0000-4000-8000-00000000D303','00000000-0000-4000-8000-00000000D203',N'DEMO-RETURN-01');
IF (SELECT COUNT(*) FROM dbo.RENTAL_CONTRACTS c JOIN @ids x ON x.contract_id=c.contract_id AND x.contract_number=c.contract_number AND x.reservation_id=c.reservation_id AND x.unit_id=c.unit_id WHERE c.customer_id='374E5607-B0B4-F111-A965-932C2C6ADDB7')<>3
 OR (SELECT COUNT(*) FROM dbo.RENTAL_CONTRACTS WHERE contract_number LIKE N'DEMO-%')<>3
 OR (SELECT COUNT(*) FROM dbo.STORAGE_UNITS u JOIN @ids x ON x.unit_id=u.unit_id WHERE u.facility_id='414E5607-B0B4-F111-A965-932C2C6ADDB7' AND u.dimensions_verified=0 AND u.readiness_verified=0)<>3
 THROW 51981,'Fixture identity or provenance differs; no reset',1;
IF EXISTS(SELECT 1 FROM dbo.SUPPORT_REQUESTS WHERE contract_id IN (SELECT contract_id FROM @ids))
 OR EXISTS(SELECT 1 FROM dbo.SUPPORT_TICKETS WHERE contract_id IN (SELECT contract_id FROM @ids))
 OR EXISTS(SELECT 1 FROM dbo.TERMINATION_REQUESTS WHERE contract_id IN (SELECT contract_id FROM @ids))
 OR EXISTS(SELECT 1 FROM dbo.DEPOSIT_REFUNDS WHERE contract_id IN (SELECT contract_id FROM @ids))
 OR EXISTS(SELECT 1 FROM dbo.EARLY_RETURN_SETTLEMENTS WHERE contract_id IN (SELECT contract_id FROM @ids))
 OR EXISTS(SELECT 1 FROM dbo.CONTRACT_COMMODITIES WHERE contract_id IN (SELECT contract_id FROM @ids))
 OR EXISTS(SELECT 1 FROM dbo.PICKUP_APPOINTMENTS WHERE contract_id IN (SELECT contract_id FROM @ids))
 OR EXISTS(SELECT 1 FROM dbo.MAINTENANCE_REQUESTS WHERE unit_id IN (SELECT unit_id FROM @ids))
 OR EXISTS(SELECT 1 FROM dbo.UNIT_MEASUREMENT_SUBMISSIONS WHERE unit_id IN (SELECT unit_id FROM @ids))
 THROW 51982,'Unexpected dependent record; no reset',1;
IF EXISTS(SELECT 1 FROM dbo.INSPECTION_REPORTS WHERE contract_id IN (SELECT contract_id FROM @ids))
 OR EXISTS(SELECT 1 FROM dbo.PENALTY_ADJUSTMENTS WHERE contract_id IN (SELECT contract_id FROM @ids))
 THROW 51983,'Inspection or adjustment requires manual review; no reset',1;

DECLARE @owned TABLE(id uniqueidentifier PRIMARY KEY);
INSERT @owned SELECT contract_id FROM @ids;
INSERT @owned SELECT renewal_id FROM dbo.RENEWALS WHERE contract_id IN (SELECT contract_id FROM @ids);
INSERT @owned SELECT invoice_id FROM dbo.INVOICES WHERE contract_id IN (SELECT contract_id FROM @ids);
INSERT @owned SELECT transaction_id FROM dbo.PAYMENT_TRANSACTIONS WHERE contract_id IN (SELECT contract_id FROM @ids);
INSERT @owned SELECT penalty_id FROM dbo.CONTRACT_PENALTIES WHERE contract_id IN (SELECT contract_id FROM @ids);
INSERT @owned SELECT handover_id FROM dbo.HANDOVER_RECORDS WHERE contract_id IN (SELECT contract_id FROM @ids);

BEGIN TRANSACTION;
DELETE FROM dbo.ACTIVITY_LOGS WHERE entity_id IN (SELECT id FROM @owned);
DELETE FROM dbo.PAYMENT_TRANSACTIONS WHERE contract_id IN (SELECT contract_id FROM @ids);
DELETE FROM dbo.CONTRACT_PENALTIES WHERE contract_id IN (SELECT contract_id FROM @ids);
DELETE FROM dbo.INVOICES WHERE contract_id IN (SELECT contract_id FROM @ids);
DELETE FROM dbo.RENEWALS WHERE contract_id IN (SELECT contract_id FROM @ids);
DELETE FROM dbo.HANDOVER_RECORDS WHERE contract_id IN (SELECT contract_id FROM @ids);
DELETE FROM dbo.RENTAL_CONTRACTS WHERE contract_id IN (SELECT contract_id FROM @ids);
DELETE FROM dbo.RESERVATIONS WHERE reservation_id IN (SELECT reservation_id FROM @ids);
DELETE FROM dbo.STORAGE_UNITS WHERE unit_id IN (SELECT unit_id FROM @ids);
DELETE FROM dbo.RENTAL_POLICIES WHERE policy_id='00000000-0000-4000-8000-00000000D018';
IF EXISTS(SELECT 1 FROM dbo.RENTAL_CONTRACTS WHERE contract_id IN (SELECT contract_id FROM @ids)) THROW 51984,'Reset incomplete',1;
COMMIT TRANSACTION;
SELECT DB_NAME() AS database_name,COUNT(*) AS remaining_demo_contracts FROM dbo.RENTAL_CONTRACTS WHERE contract_number LIKE N'DEMO-%';
