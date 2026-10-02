# SafeBox Storage — chạy demo local

Dự án dùng Java 21, Spring Boot, SQL Server, JWT và Mailpit. Giao diện khách hàng tại `http://localhost:8080/stitch/trang_ch_safebox_storage.html`.

## Tạo dữ liệu trên máy mới

1. Tạo database bằng `../database/SelfStorageFacilityDB.sql` trong SQL Server Management Studio. Không chạy lại schema gốc trên DB đã có dữ liệu.
2. Tạo các bảng và dữ liệu demo theo thứ tự: `database/03_auth_email_migration.sql`, rồi `database/04_demo_catalog_seed.sql`. Nếu muốn xem tin nguồn tham khảo riêng, chạy thêm `database/01_demo_source_listings.sql` và `database/02_demo_source_seed.sql`.
3. Trên Windows có `sqlcmd`, dùng `-f 65001` để đọc đúng dấu tiếng Việt trong seed:

```powershell
$env:DB_USERNAME = 'sa'
$env:DB_PASSWORD = '<mật khẩu SQL Server local>'
sqlcmd -S tcp:127.0.0.1,1433 -d SelfStorageFacilityDB -U $env:DB_USERNAME -P $env:DB_PASSWORD -C -I -f 65001 -b -i database/03_auth_email_migration.sql
sqlcmd -S tcp:127.0.0.1,1433 -d SelfStorageFacilityDB -U $env:DB_USERNAME -P $env:DB_PASSWORD -C -I -f 65001 -b -i database/04_demo_catalog_seed.sql
```

Hai script có thể chạy lại. Migration email chỉ đánh dấu những tài khoản có mặt khi thêm cột lần đầu; tài khoản đăng ký mới vẫn chưa xác thực. Seed catalog giữ nguyên giá hợp lệ đang hiệu lực, sửa giá 0 và thêm giá thiếu, không tạo trùng. Dữ liệu demo gồm 4 cơ sở và 6 loại kho: 5, 8, 12, 20 m² kho thường, 10 m² kho mát và 20 m² kho đông lạnh. Một loại kho hết chỗ sẽ không hiện trong API công khai.

Giá demo tính theo diện tích `width × length` trong DB: kho thường **180.000 VNĐ/m²/tháng**, kho mát **350.000 VNĐ/m²/tháng**, kho đông lạnh **450.000 VNĐ/m²/tháng**. Ví dụ 5 m² kho thường là 900.000 VNĐ/tháng; 12 m² là 2.160.000 VNĐ/tháng. Đây là giá thuê dự kiến, chưa bao gồm VAT. Không tự cộng tiền cọc, VAT hoặc phụ phí.

Ảnh `src/main/resources/static/images/demo/standard-storage.png` và `src/main/resources/static/images/demo/cold-storage.png` được tạo bằng công cụ ImageGen của Codex cho dự án này, lưu trong repo. Đây là **ảnh minh họa**, không phải ảnh thực tế hay ảnh đã xác minh. Nội dung mô tả kho, thiết bị, bảo hiểm và điều kiện thuê trong seed là **dữ liệu mô phỏng**; cần xác nhận với cơ sở trước khi dùng làm cam kết kinh doanh.

## Chạy ứng dụng và Mailpit

Xem [hướng dẫn Mailpit chi tiết](README-mailpit.md) để chạy Docker Compose hoặc executable Windows. Mailpit dùng SMTP `localhost:1025`, hộp thư `http://localhost:8025`. Chạy ứng dụng:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:DB_USERNAME = 'sa'
$env:DB_PASSWORD = '<mật khẩu SQL Server local>'
$env:JWT_SECRET = '<chuỗi ngẫu nhiên bí mật, tối thiểu 32 ký tự>'
.\mvnw.cmd spring-boot:run
```

Nếu cổng 8080 bận, đặt `$env:SERVER_PORT = '8081'` trước lệnh chạy; liên kết email mặc định tự dùng cổng này. Có thể đặt `DB_URL`, `APP_PUBLIC_BASE_URL`, `JWT_SECRET`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`, `SMTP_SSL`, `MAIL_FROM` bằng biến môi trường. Không lưu bí mật trong Git.

## Kịch bản demo

1. Đăng ký với email thử nghiệm `@safebox.test`; mở Mailpit và bấm “Xác thực tài khoản”. Trước khi xác thực, đăng nhập bị từ chối. Sau khi xác thực, đăng nhập và đăng xuất.
2. Tại trang đăng nhập, chọn “Quên mật khẩu?”, nhận email trong Mailpit, đổi mật khẩu. Mật khẩu và JWT cũ bị từ chối.
3. Vào trang tìm kho, chọn kho thường 5 m²: giá 900.000 VNĐ/tháng; đổi thời gian thành 3 tháng: tổng 2.700.000 VNĐ. Đổi loại kho để xem diện tích, ảnh và mô tả tương ứng.
4. Bấm “Thanh toán”: khách chưa đăng nhập được đưa đến trang đăng nhập và quay lại đúng lựa chọn; khách đã đăng nhập thấy thông báo nâng cấp. Nút này không tạo đặt chỗ hay giao dịch.

Địa chỉ văn phòng: **7 Đ. D1, Tăng Nhơn Phú, Hồ Chí Minh 700000, Việt Nam**; chi nhánh Bình Dương: **phường Thái Hòa, thành phố Tân Uyên**. Địa chỉ từng kho vẫn lấy riêng từ bảng `FACILITIES`.
