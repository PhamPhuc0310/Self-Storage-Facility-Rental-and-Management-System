USE SelfStorageFacilityDB;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
GO

-- Add only catalog presentation fields; existing rental and pricing records remain intact.
IF COL_LENGTH('dbo.FACILITIES', 'demo_intro') IS NULL ALTER TABLE dbo.FACILITIES ADD demo_intro NVARCHAR(MAX) NULL;
IF COL_LENGTH('dbo.FACILITIES', 'demo_safety') IS NULL ALTER TABLE dbo.FACILITIES ADD demo_safety NVARCHAR(MAX) NULL;
IF COL_LENGTH('dbo.FACILITIES', 'demo_access') IS NULL ALTER TABLE dbo.FACILITIES ADD demo_access NVARCHAR(MAX) NULL;
IF COL_LENGTH('dbo.FACILITIES', 'demo_terms') IS NULL ALTER TABLE dbo.FACILITIES ADD demo_terms NVARCHAR(MAX) NULL;
IF COL_LENGTH('dbo.FACILITIES', 'image_path') IS NULL ALTER TABLE dbo.FACILITIES ADD image_path NVARCHAR(500) NULL;
IF COL_LENGTH('dbo.STORAGE_UNIT_TYPES', 'demo_intro') IS NULL ALTER TABLE dbo.STORAGE_UNIT_TYPES ADD demo_intro NVARCHAR(MAX) NULL;
IF COL_LENGTH('dbo.STORAGE_UNIT_TYPES', 'demo_goods') IS NULL ALTER TABLE dbo.STORAGE_UNIT_TYPES ADD demo_goods NVARCHAR(MAX) NULL;
IF COL_LENGTH('dbo.STORAGE_UNIT_TYPES', 'demo_conditions') IS NULL ALTER TABLE dbo.STORAGE_UNIT_TYPES ADD demo_conditions NVARCHAR(MAX) NULL;
IF COL_LENGTH('dbo.STORAGE_UNIT_TYPES', 'image_path') IS NULL ALTER TABLE dbo.STORAGE_UNIT_TYPES ADD image_path NVARCHAR(500) NULL;
GO

BEGIN TRANSACTION;

IF NOT EXISTS (SELECT 1 FROM dbo.ROLES WHERE role_name = N'CUSTOMER')
    INSERT dbo.ROLES(role_name, description) VALUES (N'CUSTOMER', N'Khách hàng');
IF NOT EXISTS (SELECT 1 FROM dbo.ROLES WHERE role_name = N'SYSTEM_ADMIN')
    INSERT dbo.ROLES(role_name, description) VALUES (N'SYSTEM_ADMIN', N'Quản trị hệ thống');

-- A non-login technical author is needed only when a fresh database has no user
-- for PRICING_POLICIES.created_by. It has no usable BCrypt password.
IF NOT EXISTS (SELECT 1 FROM dbo.USERS)
    INSERT dbo.USERS(role_id,email,password_hash,full_name,phone,status,created_at,email_verified)
    SELECT role_id,N'catalog-seed@safebox.test',N'!seed-only-no-login!',N'Dữ liệu demo',N'0000000000',N'LOCKED',SYSUTCDATETIME(),1
    FROM dbo.ROLES WHERE role_name=N'SYSTEM_ADMIN';

-- Fresh database demo types. Existing type names and business records are kept.
IF NOT EXISTS (SELECT 1 FROM dbo.STORAGE_UNIT_TYPES WHERE type_name=N'Standard 5m2')
    INSERT dbo.STORAGE_UNIT_TYPES(type_name,storage_mode,size_name,width,length,height,features,status)
    VALUES(N'Standard 5m2',N'STANDARD',N'5 m²',2,2.5,2.8,N'Camera khu chung, khóa riêng',N'ACTIVE');
IF NOT EXISTS (SELECT 1 FROM dbo.STORAGE_UNIT_TYPES WHERE type_name=N'Standard 8m2')
    INSERT dbo.STORAGE_UNIT_TYPES(type_name,storage_mode,size_name,width,length,height,features,status)
    VALUES(N'Standard 8m2',N'STANDARD',N'8 m²',2,4,2.8,N'Camera khu chung, khóa riêng',N'ACTIVE');
IF NOT EXISTS (SELECT 1 FROM dbo.STORAGE_UNIT_TYPES WHERE type_name=N'Standard 12m2')
    INSERT dbo.STORAGE_UNIT_TYPES(type_name,storage_mode,size_name,width,length,height,features,status)
    VALUES(N'Standard 12m2',N'STANDARD',N'12 m²',3,4,3,N'Camera khu chung, khóa riêng',N'ACTIVE');
IF NOT EXISTS (SELECT 1 FROM dbo.STORAGE_UNIT_TYPES WHERE type_name=N'Standard 20m2')
    INSERT dbo.STORAGE_UNIT_TYPES(type_name,storage_mode,size_name,width,length,height,features,status)
    VALUES(N'Standard 20m2',N'STANDARD',N'20 m²',4,5,3,N'Camera khu chung, khu bốc dỡ',N'ACTIVE');
IF NOT EXISTS (SELECT 1 FROM dbo.STORAGE_UNIT_TYPES WHERE type_name=N'Cool Storage 10m2')
    INSERT dbo.STORAGE_UNIT_TYPES(type_name,storage_mode,size_name,width,length,height,min_temperature,max_temperature,features,status)
    VALUES(N'Cool Storage 10m2',N'COOL',N'10 m²',2.5,4,3,0,10,N'Kiểm soát nhiệt độ và vệ sinh',N'ACTIVE');
IF NOT EXISTS (SELECT 1 FROM dbo.STORAGE_UNIT_TYPES WHERE type_name=N'Cold Storage 20m2')
    INSERT dbo.STORAGE_UNIT_TYPES(type_name,storage_mode,size_name,width,length,height,min_temperature,max_temperature,features,status)
    VALUES(N'Cold Storage 20m2',N'COLD',N'20 m²',4,5,3,-22,-18,N'Kiểm soát nhiệt độ đông lạnh',N'ACTIVE');

-- Four small SafeBox demo facilities, inserted only when absent.
IF NOT EXISTS (SELECT 1 FROM dbo.FACILITIES WHERE name=N'SafeBox Đào Trí - Quận 7')
    INSERT dbo.FACILITIES(name,address,phone,opening_time,closing_time,status)
    VALUES(N'SafeBox Đào Trí - Quận 7',N'Đào Trí, Phường Phú Thuận, Quận 7, Thành phố Hồ Chí Minh',N'0900000001','07:00','21:00',N'ACTIVE');
IF NOT EXISTS (SELECT 1 FROM dbo.FACILITIES WHERE name=N'SafeBox Phạm Thế Hiển - Quận 8')
    INSERT dbo.FACILITIES(name,address,phone,opening_time,closing_time,status)
    VALUES(N'SafeBox Phạm Thế Hiển - Quận 8',N'Phạm Thế Hiển, Phường 6, Quận 8, Thành phố Hồ Chí Minh',N'0900000002','07:00','21:00',N'ACTIVE');
IF NOT EXISTS (SELECT 1 FROM dbo.FACILITIES WHERE name=N'SafeBox Nguyễn Văn Quỳ - Quận 7')
    INSERT dbo.FACILITIES(name,address,phone,opening_time,closing_time,status)
    VALUES(N'SafeBox Nguyễn Văn Quỳ - Quận 7',N'Nguyễn Văn Quỳ, Phường Phú Thuận, Quận 7, Thành phố Hồ Chí Minh',N'0900000003','07:00','21:00',N'ACTIVE');
IF NOT EXISTS (SELECT 1 FROM dbo.FACILITIES WHERE name=N'SafeBox Linh Xuân - Thủ Đức')
    INSERT dbo.FACILITIES(name,address,phone,opening_time,closing_time,status)
    VALUES(N'SafeBox Linh Xuân - Thủ Đức',N'Quốc lộ 1K, Phường Linh Xuân, Thành phố Thủ Đức, Thành phố Hồ Chí Minh',N'0900000004','06:30','22:00',N'ACTIVE');

-- Populate demo catalog content only where missing. Facility information is shared
-- by all types; dimensions and temperature remain on STORAGE_UNIT_TYPES.
UPDATE f SET
  demo_intro=COALESCE(f.demo_intro,CASE
    WHEN f.name LIKE N'%Đào Trí%' THEN N'Cơ sở gần trục Đào Trí, phù hợp cất đồ gia đình và hàng shop online theo ô kho riêng.'
    WHEN f.name LIKE N'%Phạm Thế Hiển%' THEN N'Cơ sở Quận 8 dành cho đồ chuyển nhà, tài liệu và hàng nhỏ cần lưu theo tháng.'
    WHEN f.name LIKE N'%Nguyễn Văn Quỳ%' THEN N'Cơ sở Phú Thuận thuận tiện cho khách ở Quận 7 gửi đồ cá nhân và hàng bán lẻ.'
    WHEN f.name LIKE N'%Linh Xuân%' THEN N'Cơ sở Linh Xuân có ô kho thường, kho mát và kho đông lạnh cho nhiều nhu cầu bảo quản.'
    ELSE N'Cơ sở lưu trữ tự quản với ô kho riêng theo diện tích đã chọn.' END),
  demo_safety=COALESCE(f.demo_safety,N'Ví dụ vận hành: camera ở lối đi, khóa riêng từng ô, kiểm soát lối vào và thiết bị PCCC tại khu chung. Khách kiểm tra điều kiện thực tế khi ký thuê.'),
  demo_access=COALESCE(f.demo_access,CASE WHEN f.name LIKE N'%Linh Xuân%'
    THEN N'Đường vào phù hợp xe tải nhỏ; có khu dừng bốc dỡ và xe đẩy dùng chung. Giờ vào kho 06:30–22:00; ngoài giờ cần liên hệ trước. Hỗ trợ vận chuyển theo thỏa thuận.'
    ELSE N'Đường vào phù hợp xe tải nhỏ; có điểm dừng bốc dỡ và xe đẩy dùng chung. Giờ vào kho 07:00–21:00; ngoài giờ cần liên hệ trước. Hỗ trợ vận chuyển theo thỏa thuận.' END),
  demo_terms=COALESCE(f.demo_terms,N'Hệ thống đang hỗ trợ thuê dự kiến 1, 2, 3, 6 hoặc 12 tháng; gia hạn liên hệ cơ sở trước khi hết hạn. Đóng gói kín, ghi nhãn và giữ lối đi thông thoáng. Không lưu chất dễ cháy nổ, hàng cấm, hàng sống hoặc hàng gây mùi. Bảo hiểm hàng hóa chưa bao gồm trong giá demo; phạm vi bảo hiểm cần thỏa thuận riêng.'),
  image_path=COALESCE(f.image_path,N'/images/demo/standard-storage.png')
FROM dbo.FACILITIES f WHERE f.name LIKE N'SafeBox %' AND f.status=N'ACTIVE';

UPDATE t SET
  demo_intro=COALESCE(t.demo_intro,CASE t.storage_mode
    WHEN N'STANDARD' THEN N'Ô kho khô riêng, phù hợp đồ gia đình, tài liệu hoặc hàng shop online theo diện tích đã chọn.'
    WHEN N'COOL' THEN N'Ô kho mát có kiểm soát nhiệt độ, phù hợp hàng cần môi trường mát và ổn định.'
    WHEN N'COLD' THEN N'Ô kho đông lạnh dành cho hàng cần bảo quản ở nhiệt độ âm.'
    ELSE N'Ô kho lưu trữ theo diện tích và điều kiện của loại kho đã chọn.' END),
  demo_goods=COALESCE(t.demo_goods,CASE t.storage_mode
    WHEN N'STANDARD' THEN N'Đồ gia đình đã đóng thùng, hồ sơ, sách và hàng bán lẻ khô. Không phù hợp thực phẩm tươi hoặc hàng cần nhiệt độ kiểm soát.'
    WHEN N'COOL' THEN N'Hàng đóng gói cần bảo quản mát; yêu cầu xác nhận nhiệt độ phù hợp cho từng mặt hàng trước khi thuê.'
    WHEN N'COLD' THEN N'Thực phẩm và hàng đã đóng gói, được phép bảo quản đông lạnh theo điều kiện sản phẩm.'
    ELSE N'Hàng hóa phù hợp theo điều kiện thuê.' END),
  demo_conditions=COALESCE(t.demo_conditions,CASE t.storage_mode
    WHEN N'STANDARD' THEN N'Kho khô, thông gió và vệ sinh định kỳ; khách tự đóng gói chống ẩm cho hàng nhạy cảm. Không có kiểm soát nhiệt độ riêng.'
    WHEN N'COOL' THEN N'Kho mát 0–10°C theo thông số demo; xếp hàng trên kệ, đóng gói kín và chừa khoảng lưu thông khí.'
    WHEN N'COLD' THEN N'Kho đông lạnh -22 đến -18°C theo thông số demo; dùng bao bì phù hợp và hạn chế mở cửa lâu.'
    ELSE N'Tuân thủ điều kiện bảo quản của loại kho.' END),
  image_path=COALESCE(t.image_path,CASE WHEN t.storage_mode IN (N'COOL',N'COLD')
    THEN N'/images/demo/cold-storage.png' ELSE N'/images/demo/standard-storage.png' END)
FROM dbo.STORAGE_UNIT_TYPES t WHERE t.status=N'ACTIVE';

-- A fresh database needs at least one available unit per public facility/type.
-- Existing unit inventories are not changed.
INSERT dbo.STORAGE_UNITS(facility_id,type_id,unit_number,status)
SELECT f.facility_id,t.type_id,CONCAT(N'DEMO-',t.type_id),N'AVAILABLE'
FROM dbo.FACILITIES f CROSS JOIN dbo.STORAGE_UNIT_TYPES t
WHERE f.name IN (N'SafeBox Đào Trí - Quận 7',N'SafeBox Phạm Thế Hiển - Quận 8',N'SafeBox Nguyễn Văn Quỳ - Quận 7',N'SafeBox Linh Xuân - Thủ Đức')
  AND t.type_name IN (N'Standard 5m2',N'Standard 8m2',N'Standard 12m2',N'Standard 20m2',N'Cool Storage 10m2',N'Cold Storage 20m2')
  AND NOT EXISTS (SELECT 1 FROM dbo.STORAGE_UNITS u WHERE u.facility_id=f.facility_id AND u.type_id=t.type_id);

DECLARE @author UNIQUEIDENTIFIER=(SELECT TOP 1 u.user_id FROM dbo.USERS u JOIN dbo.ROLES r ON r.role_id=u.role_id WHERE r.role_name=N'SYSTEM_ADMIN' ORDER BY u.created_at,u.user_id);
IF @author IS NULL SET @author=(SELECT TOP 1 user_id FROM dbo.USERS ORDER BY created_at,user_id);
IF @author IS NULL THROW 51000, 'Pricing seed requires at least one USERS row.', 1;

-- Preserve every valid effective price. Repair invalid current prices, then insert
-- only missing facility/type pairs. Rates: standard 180k, cool 350k, cold 450k/m².
UPDATE p SET monthly_price=ROUND(t.width*t.length*CASE t.storage_mode
    WHEN N'COOL' THEN 350000 WHEN N'COLD' THEN 450000 ELSE 180000 END,0)
FROM dbo.PRICING_POLICIES p JOIN dbo.STORAGE_UNIT_TYPES t ON t.type_id=p.type_id
JOIN dbo.FACILITIES f ON f.facility_id=p.facility_id
WHERE p.status=N'ACTIVE' AND p.effective_from<=CAST(GETDATE() AS date)
  AND (p.effective_to IS NULL OR p.effective_to>=CAST(GETDATE() AS date))
  AND p.monthly_price<=0 AND f.status=N'ACTIVE' AND t.status=N'ACTIVE'
  AND EXISTS (SELECT 1 FROM dbo.STORAGE_UNITS u WHERE u.facility_id=f.facility_id AND u.type_id=t.type_id AND u.status=N'AVAILABLE')
  AND NOT EXISTS (SELECT 1 FROM dbo.PRICING_POLICIES q WHERE q.facility_id=p.facility_id AND q.type_id=p.type_id AND q.status=N'ACTIVE' AND q.monthly_price>0 AND q.effective_from<=CAST(GETDATE() AS date) AND (q.effective_to IS NULL OR q.effective_to>=CAST(GETDATE() AS date)));

INSERT dbo.PRICING_POLICIES(facility_id,type_id,created_by,monthly_price,deposit_amount,daily_overdue_rate,late_payment_rate,early_termination_fee,discount_rate,fee_waiver_allowed,effective_from,effective_to,status)
SELECT f.facility_id,t.type_id,@author,
       ROUND(t.width*t.length*CASE t.storage_mode WHEN N'COOL' THEN 350000 WHEN N'COLD' THEN 450000 ELSE 180000 END,0),
       0,0,0,0,NULL,0,'2026-01-01',NULL,N'ACTIVE'
FROM dbo.FACILITIES f JOIN dbo.STORAGE_UNITS u ON u.facility_id=f.facility_id AND u.status=N'AVAILABLE'
JOIN dbo.STORAGE_UNIT_TYPES t ON t.type_id=u.type_id AND t.status=N'ACTIVE'
WHERE f.status=N'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM dbo.PRICING_POLICIES p WHERE p.facility_id=f.facility_id AND p.type_id=t.type_id
    AND p.status=N'ACTIVE' AND p.monthly_price>0 AND p.effective_from<=CAST(GETDATE() AS date)
    AND (p.effective_to IS NULL OR p.effective_to>=CAST(GETDATE() AS date)))
GROUP BY f.facility_id,t.type_id,t.width,t.length,t.storage_mode;

COMMIT TRANSACTION;
GO
