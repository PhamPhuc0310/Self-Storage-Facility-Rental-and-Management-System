USE SelfStorageFacilityDB;
GO

-- 0. Insert a dummy User & Role if they don't exist
SET IDENTITY_INSERT dbo.ROLES ON;
INSERT INTO dbo.ROLES (role_id, role_name, description) 
VALUES (1, 'ADMIN', 'Administrator Role');
SET IDENTITY_INSERT dbo.ROLES OFF;

DECLARE @Admin_ID UNIQUEIDENTIFIER = NEWID();
INSERT INTO dbo.USERS (user_id, role_id, email, password_hash, full_name, phone, status, created_at)
VALUES (@Admin_ID, 1, 'admin@safebox.vn', 'hashedpass', 'System Admin', '0123456789', 'ACTIVE', GETDATE());

-- 1. Insert Facility
DECLARE @Facility_ID UNIQUEIDENTIFIER = NEWID();
INSERT INTO dbo.FACILITIES (facility_id, name, address, latitude, longitude, phone, opening_time, closing_time, status)
VALUES (@Facility_ID, N'SafeBox Thu Duc Hub', N'Song Hanh Hanoi Highway, Thu Duc City, HCMC', 10.8492, 106.7725, '02838229900', '08:00:00', '22:00:00', 'ACTIVE');

-- 2. Insert Storage Unit Types
SET IDENTITY_INSERT dbo.STORAGE_UNIT_TYPES ON;

INSERT INTO dbo.STORAGE_UNIT_TYPES (type_id, type_name, storage_mode, size_name, width, length, height, features, status)
VALUES 
(1, N'Small Standard', N'Standard Dry Storage', N'Small', 2.0, 2.5, 2.5, N'Standard Dry Storage', 'ACTIVE'),
(2, N'Medium Standard', N'Standard Dry Storage', N'Medium', 3.0, 5.0, 3.0, N'Standard Dry Storage, Direct Lift', 'ACTIVE'),
(3, N'Cold Storage Locker', N'Cold 18°C', N'Large', 4.0, 5.0, 3.0, N'Cold & Climate Storage Zone', 'ACTIVE');

SET IDENTITY_INSERT dbo.STORAGE_UNIT_TYPES OFF;

-- 3. Insert Pricing Policies
INSERT INTO dbo.PRICING_POLICIES (
    pricing_id, facility_id, type_id, created_by, monthly_price, deposit_amount, 
    daily_overdue_rate, late_payment_rate, early_termination_fee, fee_waiver_allowed, effective_from, status
)
VALUES 
(NEWID(), @Facility_ID, 1, @Admin_ID, 1200000, 1200000, 50000, 100000, 0, 0, GETDATE(), 'ACTIVE'),
(NEWID(), @Facility_ID, 2, @Admin_ID, 2500000, 2500000, 50000, 100000, 0, 0, GETDATE(), 'ACTIVE'),
(NEWID(), @Facility_ID, 3, @Admin_ID, 3800000, 3800000, 50000, 100000, 0, 0, GETDATE(), 'ACTIVE');

-- 4. Insert Storage Units (Available for rent)
INSERT INTO dbo.STORAGE_UNITS (unit_id, facility_id, type_id, unit_number, status) VALUES (NEWID(), @Facility_ID, 1, 'S-101', 'AVAILABLE');
INSERT INTO dbo.STORAGE_UNITS (unit_id, facility_id, type_id, unit_number, status) VALUES (NEWID(), @Facility_ID, 1, 'S-102', 'AVAILABLE');
INSERT INTO dbo.STORAGE_UNITS (unit_id, facility_id, type_id, unit_number, status) VALUES (NEWID(), @Facility_ID, 2, 'M-201', 'AVAILABLE');
INSERT INTO dbo.STORAGE_UNITS (unit_id, facility_id, type_id, unit_number, status) VALUES (NEWID(), @Facility_ID, 3, 'C-301', 'AVAILABLE');

PRINT 'Sample data inserted successfully!';
GO
