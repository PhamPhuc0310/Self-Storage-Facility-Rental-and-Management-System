USE SelfStorageFacilityDB;
GO
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @hash NVARCHAR(255) = N'$2a$10$LpWaw5WL7aVwdCMwtd4TmeovH.XJrs8PrZjaQMyBJjVrh2cQHE8c2';

UPDATE dbo.USERS
SET password_hash = @hash,
    status = N'ACTIVE',
    email_verified = 1
WHERE email IN (
    'admin@safebox.vn',
    'manager.q7@safebox.vn',
    'manager.thuduc@safebox.vn',
    'quanly@safebox.vn',
    'staff.q7@safebox.vn',
    'staff.q8@safebox.vn',
    'staff.thuduc@safebox.vn',
    'customer1@safebox.vn',
    'customer2@safebox.vn',
    'customer3@safebox.vn',
    'khachhang@safebox.vn',
    'business@safebox.vn',
    'admin-legacy@safebox.vn',
    'customer.demo@safebox.test',
    'staff.demo@safebox.test',
    'manager.demo@safebox.test'
);
GO

SELECT email, password_hash, status, email_verified
FROM dbo.USERS
WHERE email IN ('admin@safebox.vn', 'customer1@safebox.vn', 'manager.q7@safebox.vn', 'staff.q7@safebox.vn');
GO
