:ON ERROR EXIT
-- Isolated SQL-only rehearsal. These are synthetic opening states, not real rentals,
-- measurements, cash receipts, bank settlements or policy approvals.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
IF DB_NAME() <> N'SafeBox_FinalRehearsal_20261030'
    THROW 51930, 'Only the isolated final rehearsal restore is allowed', 1;
IF COL_LENGTH('dbo.STORAGE_UNITS','dimensions_verified') IS NULL
    OR COL_LENGTH('dbo.PAYMENT_TRANSACTIONS','confirmed_at') IS NULL
    OR OBJECT_ID('dbo.CONTRACT_PENALTIES','U') IS NULL
    THROW 51931, 'Rehearsal schema incomplete', 1;

DECLARE @facility uniqueidentifier='414E5607-B0B4-F111-A965-932C2C6ADDB7',
        @customer uniqueidentifier='374E5607-B0B4-F111-A965-932C2C6ADDB7',
        @staff uniqueidentifier='384E5607-B0B4-F111-A965-932C2C6ADDB7',
        @manager uniqueidentifier='3B4E5607-B0B4-F111-A965-932C2C6ADDB7',
        @business uniqueidentifier='3D4E5607-B0B4-F111-A965-932C2C6ADDB7',
        @price uniqueidentifier='6E292355-68BE-F111-A965-932C2C6ADDB7',
        @policy uniqueidentifier='00000000-0000-4000-8000-00000000D018',
        @today date=CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS date);

IF NOT EXISTS(SELECT 1 FROM dbo.USERS WHERE user_id=@customer AND status='ACTIVE')
 OR NOT EXISTS(SELECT 1 FROM dbo.USERS WHERE user_id=@staff AND status='ACTIVE')
 OR NOT EXISTS(SELECT 1 FROM dbo.USERS WHERE user_id=@manager AND status='ACTIVE')
 OR NOT EXISTS(SELECT 1 FROM dbo.USERS WHERE user_id=@business AND status='ACTIVE')
 OR NOT EXISTS(SELECT 1 FROM dbo.USER_FACILITY_ASSIGNMENTS WHERE user_id=@staff AND facility_id=@facility AND status='ACTIVE')
 OR NOT EXISTS(SELECT 1 FROM dbo.USER_FACILITY_ASSIGNMENTS WHERE user_id=@manager AND facility_id=@facility AND status='ACTIVE')
 OR NOT EXISTS(SELECT 1 FROM dbo.PRICING_POLICIES WHERE pricing_id=@price AND facility_id=@facility AND type_id=1 AND status='ACTIVE' AND effective_from<=@today AND (effective_to IS NULL OR effective_to>=@today))
    THROW 51932, 'Actor, facility scope or price precondition failed', 1;
IF EXISTS(SELECT 1 FROM dbo.RENTAL_CONTRACTS WHERE contract_number IN (N'DEMO-RENEW-01',N'DEMO-OVERDUE-01',N'DEMO-RETURN-01'))
 OR EXISTS(SELECT 1 FROM dbo.STORAGE_UNITS WHERE unit_id IN ('00000000-0000-4000-8000-00000000D201','00000000-0000-4000-8000-00000000D202','00000000-0000-4000-8000-00000000D203') OR (facility_id=@facility AND unit_number IN (N'SWP-R-01',N'SWP-O-01',N'SWP-T-01')))
 OR EXISTS(SELECT 1 FROM dbo.RESERVATIONS WHERE reservation_id IN ('00000000-0000-4000-8000-00000000D301','00000000-0000-4000-8000-00000000D302','00000000-0000-4000-8000-00000000D303'))
    THROW 51933, 'Synthetic identifier collision or prior setup; no writes made', 1;
IF EXISTS(SELECT 1 FROM dbo.RENTAL_POLICIES WHERE policy_id=@policy)
    THROW 51934, 'Synthetic policy identifier collision', 1;

BEGIN TRANSACTION;
-- Test-only policy surrogate copied from the approved Q7 TEST policy's 3-day,
-- PERCENT_DAILY 0.05 terms. The primary has no equivalent approval record.
INSERT dbo.RENTAL_POLICIES(policy_id,facility_id,renewal_open_days,priority_deadline_days,renewal_payment_hours,reservation_hold_minutes,overdue_grace_days,cancellation_policy,return_policy,effective_from,status,daily_penalty_rate,fixed_penalty_amount,penalty_type,overdue_approved_at,overdue_approved_by,overdue_version,overdue_created_by)
SELECT @policy,@facility,p.renewal_open_days,p.priority_deadline_days,p.renewal_payment_hours,p.reservation_hold_minutes,3,p.cancellation_policy,p.return_policy,DATEADD(day,-10,@today),'ACTIVE',0.05,0,'PERCENT_DAILY',SYSDATETIME(),@business,9001,@business
FROM dbo.RENTAL_POLICIES p WHERE p.facility_id IS NULL AND p.status='ACTIVE';
IF @@ROWCOUNT<>1 THROW 51935, 'Expected one global policy source', 1;

INSERT dbo.STORAGE_UNITS(unit_id,facility_id,type_id,unit_number,status,condition_note,readiness_verified,dimensions_verified)
VALUES
 ('00000000-0000-4000-8000-00000000D201',@facility,1,N'SWP-R-01','OCCUPIED',N'SYNTHETIC DEMO-RENEW-01; no physical measurements claimed',0,0),
 ('00000000-0000-4000-8000-00000000D202',@facility,1,N'SWP-O-01','OCCUPIED',N'SYNTHETIC DEMO-OVERDUE-01; no physical measurements claimed',0,0),
 ('00000000-0000-4000-8000-00000000D203',@facility,1,N'SWP-T-01','OCCUPIED',N'SYNTHETIC DEMO-RETURN-01; no physical measurements claimed',0,0);
INSERT dbo.RESERVATIONS(reservation_id,customer_id,facility_id,type_id,pricing_id,start_date,end_date,estimated_rental_amount,estimated_deposit_amount,status,quoted_monthly_price,pricing_version)
VALUES
 ('00000000-0000-4000-8000-00000000D301',@customer,@facility,1,@price,DATEADD(day,-25,@today),DATEADD(day,5,@today),900000,0,'CONFIRMED',900000,'v2'),
 ('00000000-0000-4000-8000-00000000D302',@customer,@facility,1,@price,DATEADD(day,-34,@today),DATEADD(day,-4,@today),900000,0,'CONFIRMED',900000,'v2'),
 ('00000000-0000-4000-8000-00000000D303',@customer,@facility,1,@price,DATEADD(day,-23,@today),DATEADD(day,7,@today),900000,0,'CONFIRMED',900000,'v2');
INSERT dbo.RENTAL_CONTRACTS(contract_id,contract_number,reservation_id,customer_id,unit_id,start_date,end_date,monthly_rental_fee,deposit_amount,billing_cycle,terms_version,status)
VALUES
 ('00000000-0000-4000-8000-00000000D401',N'DEMO-RENEW-01','00000000-0000-4000-8000-00000000D301',@customer,'00000000-0000-4000-8000-00000000D201',DATEADD(day,-25,@today),DATEADD(day,5,@today),900000,0,'MONTHLY','v2','ACTIVE'),
 ('00000000-0000-4000-8000-00000000D402',N'DEMO-OVERDUE-01','00000000-0000-4000-8000-00000000D302',@customer,'00000000-0000-4000-8000-00000000D202',DATEADD(day,-34,@today),DATEADD(day,-4,@today),900000,0,'MONTHLY','v2','ACTIVE'),
 ('00000000-0000-4000-8000-00000000D403',N'DEMO-RETURN-01','00000000-0000-4000-8000-00000000D303',@customer,'00000000-0000-4000-8000-00000000D203',DATEADD(day,-23,@today),DATEADD(day,7,@today),900000,0,'MONTHLY','v2','ACTIVE');
INSERT dbo.HANDOVER_RECORDS(handover_id,contract_id,unit_id,staff_id,handover_type,scheduled_at,actual_at,note,status)
VALUES
 ('00000000-0000-4000-8000-00000000D501','00000000-0000-4000-8000-00000000D401','00000000-0000-4000-8000-00000000D201',@staff,'CHECK_IN',DATEADD(hour,8,CONVERT(datetime2,DATEADD(day,-25,@today))),DATEADD(hour,9,CONVERT(datetime2,DATEADD(day,-25,@today))),N'SYNTHETIC DEMO opening state','COMPLETED'),
 ('00000000-0000-4000-8000-00000000D502','00000000-0000-4000-8000-00000000D402','00000000-0000-4000-8000-00000000D202',@staff,'CHECK_IN',DATEADD(hour,8,CONVERT(datetime2,DATEADD(day,-34,@today))),DATEADD(hour,9,CONVERT(datetime2,DATEADD(day,-34,@today))),N'SYNTHETIC DEMO opening state','COMPLETED'),
 ('00000000-0000-4000-8000-00000000D503','00000000-0000-4000-8000-00000000D403','00000000-0000-4000-8000-00000000D203',@staff,'CHECK_IN',DATEADD(hour,8,CONVERT(datetime2,DATEADD(day,-23,@today))),DATEADD(hour,9,CONVERT(datetime2,DATEADD(day,-23,@today))),N'SYNTHETIC DEMO opening state','COMPLETED');
COMMIT TRANSACTION;
SELECT DB_NAME() AS rehearsal_database,contract_number,status,end_date FROM dbo.RENTAL_CONTRACTS WHERE contract_number LIKE N'DEMO-%' ORDER BY contract_number;
