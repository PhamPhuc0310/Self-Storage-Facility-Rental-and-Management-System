-- A source listing is reference/demo content, never a SafeBox bookable unit.
-- Run after 01_demo_source_listings.sql. Re-running adds no duplicates.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF NOT EXISTS (SELECT 1 FROM dbo.DEMO_SOURCE_LISTINGS WHERE source_listing_id = N'batdongsan-46288340')
BEGIN
    INSERT INTO dbo.DEMO_SOURCE_LISTINGS
        (source_listing_id, source_url, title, address, source_area_m2,
         quoted_price, price_unit, description, amenities, rental_terms,
         cover_image_path, image_rights_note, posted_on, collected_on)
    VALUES
        (N'batdongsan-46288340',
         N'https://batdongsan.com.vn/cho-thue-kho-nha-xuong-dat-duong-nguyen-van-linh-phuong-tan-thuan-tay-1-59/cho-q7-5-000m-chia-dien-tich-hoat-pccc-ay-u-container-ra-vao-thoai-mai-pr46288340',
         N'Cho thuê kho Q7, 5.000m² chia diện tích linh hoạt',
         N'Đường Nguyễn Văn Linh, Phường Tân Thuận Tây, Quận 7, TP.HCM',
         5000.00, 70000.00, N'VND/m²/tháng',
         N'Tin đăng mô tả kho riêng tự quản hoặc kho chung có thủ kho; diện tích có thể chia theo thỏa thuận.',
         N'Container và xe tải ra vào; xe nâng, bốc xếp, đóng gói, WMS; PCCC, camera, bảo vệ và bảo hiểm kho theo tin đăng.',
         N'Giá từ 70.000 VND/m²/tháng, thay đổi theo diện tích và tiêu chuẩn kho.',
         NULL, N'Ảnh nguồn không tải được vào môi trường phát triển; quyền tái sử dụng chưa được xác minh.',
         '2026-09-28', '2026-10-02');
END;
COMMIT TRANSACTION;
