USE SelfStorageFacilityDB;
GO

-- 1. Insert missing roles
IF NOT EXISTS (SELECT 1 FROM dbo.ROLES WHERE role_name = N'CUSTOMER')
    INSERT dbo.ROLES(role_name, description) VALUES (N'CUSTOMER', N'Khách hàng');

IF NOT EXISTS (SELECT 1 FROM dbo.ROLES WHERE role_name = N'FACILITY_STAFF')
    INSERT dbo.ROLES(role_name, description) VALUES (N'FACILITY_STAFF', N'Nhân viên cơ sở');

IF NOT EXISTS (SELECT 1 FROM dbo.ROLES WHERE role_name = N'FACILITY_MANAGER')
    INSERT dbo.ROLES(role_name, description) VALUES (N'FACILITY_MANAGER', N'Quản lý cơ sở');

IF NOT EXISTS (SELECT 1 FROM dbo.ROLES WHERE role_name = N'BUSINESS_OPERATIONS_MANAGER')
    INSERT dbo.ROLES(role_name, description) VALUES (N'BUSINESS_OPERATIONS_MANAGER', N'Quản lý vận hành kinh doanh');

IF NOT EXISTS (SELECT 1 FROM dbo.ROLES WHERE role_name = N'SYSTEM_ADMIN')
    INSERT dbo.ROLES(role_name, description) VALUES (N'SYSTEM_ADMIN', N'Quản trị hệ thống');
GO

-- Password hash for '123456' is $2a$10$4VE3A4zembAlqQSSFD.Bee3pFqYHBNNMxLoaQfZ.WTAwozEgiR8lC
DECLARE @pass NVARCHAR(255) = N'$2a$10$4VE3A4zembAlqQSSFD.Bee3pFqYHBNNMxLoaQfZ.WTAwozEgiR8lC';

-- 2. Insert Admin
IF NOT EXISTS (SELECT 1 FROM dbo.USERS WHERE email = N'admin@safebox.vn')
BEGIN
    INSERT INTO dbo.USERS(role_id, email, password_hash, full_name, phone, identity_number, address, status, created_at, email_verified)
    SELECT role_id, N'admin@safebox.vn', @pass, N'Quản Trị Viên', N'0901234567', N'001200000001', N'TP. Hồ Chí Minh', N'ACTIVE', SYSDATETIME(), 1
    FROM dbo.ROLES WHERE role_name = N'SYSTEM_ADMIN';
END;

-- 3. Insert Manager
IF NOT EXISTS (SELECT 1 FROM dbo.USERS WHERE email = N'manager@safebox.vn')
BEGIN
    INSERT INTO dbo.USERS(role_id, email, password_hash, full_name, phone, identity_number, address, status, created_at, email_verified)
    SELECT role_id, N'manager@safebox.vn', @pass, N'Quản Lý Cơ Sở', N'0901234568', N'001200000002', N'Quận 7, TP. HCM', N'ACTIVE', SYSDATETIME(), 1
    FROM dbo.ROLES WHERE role_name = N'FACILITY_MANAGER';
END;

-- 4. Insert Staff
IF NOT EXISTS (SELECT 1 FROM dbo.USERS WHERE email = N'staff@safebox.vn')
BEGIN
    INSERT INTO dbo.USERS(role_id, email, password_hash, full_name, phone, identity_number, address, status, created_at, email_verified)
    SELECT role_id, N'staff@safebox.vn', @pass, N'Nhân Viên Kho', N'0901234569', N'001200000003', N'Quận 7, TP. HCM', N'ACTIVE', SYSDATETIME(), 1
    FROM dbo.ROLES WHERE role_name = N'FACILITY_STAFF';
END;

-- 5. Insert Customer
IF NOT EXISTS (SELECT 1 FROM dbo.USERS WHERE email = N'customer@safebox.vn')
BEGIN
    INSERT INTO dbo.USERS(role_id, email, password_hash, full_name, phone, identity_number, address, status, created_at, email_verified)
    SELECT role_id, N'customer@safebox.vn', @pass, N'Nguyễn Văn Khách', N'0901234570', N'001200000004', N'Quận 1, TP. HCM', N'ACTIVE', SYSDATETIME(), 1
    FROM dbo.ROLES WHERE role_name = N'CUSTOMER';
END;
GO

-- 6. Assign Manager & Staff to Facility
DECLARE @managerId UNIQUEIDENTIFIER = (SELECT user_id FROM dbo.USERS WHERE email = N'manager@safebox.vn');
DECLARE @staffId UNIQUEIDENTIFIER = (SELECT user_id FROM dbo.USERS WHERE email = N'staff@safebox.vn');
DECLARE @adminId UNIQUEIDENTIFIER = (SELECT user_id FROM dbo.USERS WHERE email = N'admin@safebox.vn');
DECLARE @facilityId UNIQUEIDENTIFIER = (SELECT TOP 1 facility_id FROM dbo.FACILITIES WHERE name LIKE N'%Đào Trí%');

IF @managerId IS NOT NULL AND @facilityId IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.USER_FACILITY_ASSIGNMENTS WHERE user_id = @managerId AND facility_id = @facilityId)
    BEGIN
        INSERT INTO dbo.USER_FACILITY_ASSIGNMENTS(user_id, facility_id, assigned_by, start_date, status)
        VALUES (@managerId, @facilityId, @adminId, CAST(GETDATE() AS DATE), N'ACTIVE');
    END;
END;

IF @staffId IS NOT NULL AND @facilityId IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.USER_FACILITY_ASSIGNMENTS WHERE user_id = @staffId AND facility_id = @facilityId)
    BEGIN
        INSERT INTO dbo.USER_FACILITY_ASSIGNMENTS(user_id, facility_id, assigned_by, start_date, status)
        VALUES (@staffId, @facilityId, @adminId, CAST(GETDATE() AS DATE), N'ACTIVE');
    END;
END;
GO
