# UC02 / UC04: dữ liệu kho và tin tham khảo

Ứng dụng đọc cơ sở, ô kho, loại kho và bảng giá từ SQL Server `SelfStorageFacilityDB`. Trong cấu hình hiện tại, API danh sách là `GET /api/facilities?page=0&size=8`, chi tiết là `GET /api/facilities/{facilityId}`, báo giá là `GET /api/pricing/estimate?facilityId=...&typeId=...&months=...`. Hai API đầu cho guest truy cập. `unitTypes` chỉ chứa loại `ACTIVE` có ít nhất một ô kho ở trạng thái `AVAILABLE` tại cơ sở. Giá có thể `null` khi không có bảng giá `ACTIVE` đang hiệu lực hôm nay. Nếu nhiều giá cùng hiệu lực, chọn `effective_from` mới nhất; khi trùng ngày, chọn `pricing_id` lớn nhất để kết quả ổn định. Con số `AVAILABLE` là trạng thái ô kho hiện tại, chưa kiểm tra lịch đặt theo ngày.

## Cài đặt dữ liệu

DB mới: chạy schema gốc `../database/SelfStorageFacilityDB.sql` một lần trong SSMS, sau đó chạy `database/01_demo_source_listings.sql`, rồi `database/02_demo_source_seed.sql`.

DB đã tồn tại: **không chạy lại schema gốc**; chỉ chạy hai script trong thư mục `database` trên database `SelfStorageFacilityDB`, theo đúng thứ tự. Script tạo bảng riêng nếu chưa có. Seed dùng transaction và khóa `source_listing_id`/`source_url` để chạy lại không tạo trùng. Hai script không sửa bảng SafeBox đang có. Kiểm tra:

```sql
SELECT source_listing_id, source_url, source_area_m2, quoted_price,
       price_unit, cover_image_path, demo_only
FROM dbo.DEMO_SOURCE_LISTINGS;

SELECT f.facility_id, f.name, f.status, u.type_id, t.type_name,
       t.status AS type_status, u.status AS unit_status, COUNT(*) AS unit_count
FROM dbo.FACILITIES f
JOIN dbo.STORAGE_UNITS u ON u.facility_id = f.facility_id
JOIN dbo.STORAGE_UNIT_TYPES t ON t.type_id = u.type_id
GROUP BY f.facility_id, f.name, f.status, u.type_id,
         t.type_name, t.status, u.status;

SELECT facility_id, type_id, status, effective_from, effective_to, monthly_price
FROM dbo.PRICING_POLICIES;
```

## Phạm vi thu thập và giới hạn

Ngày thu thập: 02/10/2026. Đã xem [trang danh sách 1](https://batdongsan.com.vn/cho-thue-kho-nha-xuong-dat-tp-hcm), [trang 2](https://batdongsan.com.vn/cho-thue-kho-nha-xuong-dat-tp-hcm/p2) và [chi tiết mã tin 46288340](https://batdongsan.com.vn/cho-thue-kho-nha-xuong-dat-duong-nguyen-van-linh-phuong-tan-thuan-tay-1-59/cho-q7-5-000m-chia-dien-tich-hoat-pccc-ay-u-container-ra-vao-thoai-mai-pr46288340). Giữ 1 tin kho; bỏ qua các tin đất/xưởng hoặc chưa kiểm chứng phù hợp. Chi tiết mã 46346513 và ảnh của mã 46288340 không truy cập được từ môi trường này. Đây không phải toàn bộ website.

Tin 46288340 là kho 5.000 m² trên đường Nguyễn Văn Linh, chào giá **từ 70.000 VND/m²/tháng**, không phải giá thuê cho ô SafeBox 5–20 m². Tin nguồn không xác minh tình trạng đặt theo ngày, kích thước từng ô hoặc quan hệ với cơ sở SafeBox Đào Trí. Vì vậy dữ liệu nằm ở `DEMO_SOURCE_LISTINGS`, không được gắn vào `FACILITIES`, `STORAGE_UNITS` hay `PRICING_POLICIES`. Nội dung mô tả trong seed là tóm tắt có nguồn. `cover_image_path` để `NULL` vì ảnh không tải được; không hotlink hoặc gắn ảnh của kho khác. `image_rights_note` ghi tình trạng quyền sử dụng chưa xác minh.

Trước khi chạy ứng dụng trên máy khác, cấu hình JDBC và bí mật qua biến môi trường hoặc cấu hình local riêng; không commit mật khẩu/JWT secret. Chạy `database/04_demo_catalog_seed.sql` để có bảng giá SafeBox mô phỏng theo diện tích kho nhỏ. Tin tham khảo không thay thế bảng giá SafeBox. Xem [README chính](README.md) để biết thứ tự setup đầy đủ.
