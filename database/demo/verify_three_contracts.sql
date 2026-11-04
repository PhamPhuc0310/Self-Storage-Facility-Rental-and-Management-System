-- Read-only baseline verification. Pass -v SafeBoxDemoDb="<reviewed target>".
:ON ERROR EXIT
IF DB_NAME() <> N'$(SafeBoxDemoDb)'
    THROW 51900, 'Wrong database', 1;
IF COL_LENGTH('dbo.STORAGE_UNITS','dimensions_verified') IS NULL
    OR COL_LENGTH('dbo.PAYMENT_TRANSACTIONS','confirmed_at') IS NULL
    OR OBJECT_ID('dbo.STAFF_REGION_ASSIGNMENTS','U') IS NULL
    THROW 51901, 'Demo schema migrations incomplete', 1;
IF (SELECT COUNT(*) FROM dbo.RENTAL_CONTRACTS WHERE contract_number IN (N'DEMO-RENEW-01',N'DEMO-OVERDUE-01',N'DEMO-RETURN-01')) <> 3
    THROW 51902, 'Exactly three marked contracts required', 1;
IF (SELECT COUNT(DISTINCT customer_id) FROM dbo.RENTAL_CONTRACTS WHERE contract_number IN (N'DEMO-RENEW-01',N'DEMO-OVERDUE-01',N'DEMO-RETURN-01')) <> 1
 OR (SELECT COUNT(DISTINCT unit_id) FROM dbo.RENTAL_CONTRACTS WHERE contract_number IN (N'DEMO-RENEW-01',N'DEMO-OVERDUE-01',N'DEMO-RETURN-01')) <> 3
    THROW 51903, 'Customer or three-unit fixture invariant failed', 1;
IF EXISTS(SELECT 1 FROM dbo.RENTAL_CONTRACTS c JOIN dbo.STORAGE_UNITS u ON u.unit_id=c.unit_id
          WHERE c.contract_number IN (N'DEMO-RENEW-01',N'DEMO-OVERDUE-01',N'DEMO-RETURN-01')
            AND (c.status<>N'ACTIVE' OR u.status<>N'OCCUPIED'))
    THROW 51904, 'Fixture is no longer at opening state', 1;
IF EXISTS(SELECT 1 FROM sys.foreign_keys WHERE is_disabled=1 OR is_not_trusted=1)
    THROW 51905, 'Foreign key disabled or untrusted', 1;
SELECT DB_NAME() AS database_name, DB_ID() AS database_id, @@SERVERNAME AS server_name;
SELECT c.contract_number,c.status,c.start_date,c.end_date,c.customer_id,c.unit_id,
       u.status AS unit_status,u.dimensions_verified,u.readiness_verified,
       u.facility_id
FROM dbo.RENTAL_CONTRACTS c JOIN dbo.STORAGE_UNITS u ON u.unit_id=c.unit_id
WHERE c.contract_number IN (N'DEMO-RENEW-01',N'DEMO-OVERDUE-01',N'DEMO-RETURN-01');
SELECT c.contract_number,i.invoice_number,i.invoice_type,i.status,i.total_amount,i.paid_amount
FROM dbo.RENTAL_CONTRACTS c JOIN dbo.INVOICES i ON i.contract_id=c.contract_id
WHERE c.contract_number IN (N'DEMO-RENEW-01',N'DEMO-OVERDUE-01',N'DEMO-RETURN-01');
SELECT c.contract_number,p.payment_type,p.payment_method,p.status,p.confirmed_by,p.confirmed_at
FROM dbo.RENTAL_CONTRACTS c JOIN dbo.PAYMENT_TRANSACTIONS p ON p.contract_id=c.contract_id
WHERE c.contract_number IN (N'DEMO-RENEW-01',N'DEMO-OVERDUE-01',N'DEMO-RETURN-01');
