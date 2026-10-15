USE SelfStorageFacilityDB;
GO

-- Script tạo dữ liệu mẫu cho Hợp đồng thuê kho và Hóa đơn thanh toán ban đầu (UC09, UC10)
-- Dành cho tài khoản test: customer@safebox.vn

DECLARE @customerId UNIQUEIDENTIFIER = (SELECT user_id FROM dbo.USERS WHERE email = N'customer@safebox.vn');
DECLARE @managerId UNIQUEIDENTIFIER = (SELECT user_id FROM dbo.USERS WHERE email = N'manager@safebox.vn');
DECLARE @facilityId UNIQUEIDENTIFIER = (SELECT TOP 1 facility_id FROM dbo.FACILITIES WHERE status = N'ACTIVE');
DECLARE @typeId INT = (SELECT TOP 1 type_id FROM dbo.STORAGE_UNIT_TYPES WHERE is_active = 1);
DECLARE @pricingId UNIQUEIDENTIFIER = (SELECT TOP 1 pricing_id FROM dbo.PRICING_POLICIES WHERE facility_id = @facilityId AND type_id = @typeId AND status = N'ACTIVE');
DECLARE @unitId UNIQUEIDENTIFIER = (SELECT TOP 1 unit_id FROM dbo.STORAGE_UNITS WHERE facility_id = @facilityId AND type_id = @typeId AND status = N'AVAILABLE');

IF @customerId IS NOT NULL AND @facilityId IS NOT NULL AND @typeId IS NOT NULL AND @pricingId IS NOT NULL AND @unitId IS NOT NULL
BEGIN
    -- 1. Tạo đơn đặt kho CONFIRMED nếu chưa có
    DECLARE @resId UNIQUEIDENTIFIER = NEWID();
    DECLARE @startDate DATE = CAST(GETDATE() AS DATE);
    DECLARE @endDate DATE = DATEADD(MONTH, 3, @startDate);

    IF NOT EXISTS (SELECT 1 FROM dbo.RESERVATIONS WHERE customer_id = @customerId AND status = N'CONFIRMED')
    BEGIN
        INSERT INTO dbo.RESERVATIONS (
            reservation_id, customer_id, facility_id, type_id, pricing_id,
            start_date, end_date, estimated_rental_amount, estimated_deposit_amount,
            status, created_at
        ) VALUES (
            @resId, @customerId, @facilityId, @typeId, @pricingId,
            @startDate, @endDate, 4500000.00, 1500000.00,
            N'CONFIRMED', SYSDATETIME()
        );
    END
    ELSE
    BEGIN
        SELECT TOP 1 @resId = reservation_id FROM dbo.RESERVATIONS WHERE customer_id = @customerId AND status = N'CONFIRMED';
    END;

    -- 2. Tạo Hợp đồng thuê kho nếu chưa có
    IF NOT EXISTS (SELECT 1 FROM dbo.RENTAL_CONTRACTS WHERE reservation_id = @resId)
    BEGIN
        DECLARE @contractId UNIQUEIDENTIFIER = NEWID();
        DECLARE @contractNumber NVARCHAR(50) = N'HD-' + FORMAT(GETDATE(), 'yyyyMMdd') + N'-TEST';

        INSERT INTO dbo.RENTAL_CONTRACTS (
            contract_id, contract_number, reservation_id, customer_id, unit_id,
            start_date, end_date, monthly_rental_fee, deposit_amount, billing_cycle,
            terms_version, approved_by, approved_at, company_signature,
            status, created_at
        ) VALUES (
            @contractId, @contractNumber, @resId, @customerId, @unitId,
            @startDate, @endDate, 1500000.00, 1500000.00, N'MONTHLY',
            N'TERMS-2026-V1', @managerId, SYSDATETIME(), N'SafeBox Storage Vietnam',
            N'PENDING_CONFIRMATION', SYSDATETIME()
        );

        PRINT N'Đã tạo thành công hợp đồng mẫu: ' + @contractNumber;
    END;
END;
GO
