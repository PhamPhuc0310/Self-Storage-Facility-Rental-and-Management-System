# Demo xác thực email và đặt lại mật khẩu với Mailpit

## Chuẩn bị SQL Server

Chạy schema `../database/SelfStorageFacilityDB.sql` và dữ liệu vai trò/tài khoản hiện có trước. Chạy migration **một lần trước khi mở đăng ký mới**:

```powershell
sqlcmd -S tcp:127.0.0.1,1433 -d SelfStorageFacilityDB -U $env:DB_USERNAME -P $env:DB_PASSWORD -C -I -f 65001 -b -i database/03_auth_email_migration.sql
```

Migration thêm `USERS.email_verified`, đánh dấu tài khoản đã có là xác thực, và tạo `AUTH_TOKENS`. Sau đó chạy `database/04_demo_catalog_seed.sql` với `sqlcmd -f 65001` để có giá, mô tả và ảnh demo. Có thể chạy lại: tài khoản đăng ký mới chưa xác thực vẫn giữ nguyên trạng thái. Ứng dụng dùng `spring.jpa.hibernate.ddl-auto=none`, nên migration là bắt buộc. Seed tạo vai trò `CUSTOMER` khi DB mới chưa có.

Đặt `DB_USERNAME`, `DB_PASSWORD` và nếu cần `DB_URL` trong môi trường chạy, không ghi mật khẩu vào file trong Git. `JWT_SECRET` cần chuỗi ngẫu nhiên ít nhất 32 byte khi triển khai ngoài máy demo.

## Chạy Mailpit

### Docker

Tại thư mục `self-storage`:

```powershell
docker compose -f compose.mailpit.yml up -d
```

Compose chỉ mở SMTP `127.0.0.1:1025` và giao diện `127.0.0.1:8025` trên máy local.

### Windows không có Docker

Tải `mailpit-windows-amd64.zip` từ [trang phát hành Mailpit chính thức](https://github.com/axllent/mailpit/releases), giải nén, rồi chạy trong PowerShell tại thư mục chứa `mailpit.exe`:

```powershell
.\mailpit.exe --listen 127.0.0.1:8025 --smtp 127.0.0.1:1025
```

Giữ cửa sổ này mở. Hộp thư demo: [http://localhost:8025](http://localhost:8025). Mailpit nhận thư thử nghiệm và không gửi ra Internet.

## Chạy Spring Boot

Trong một PowerShell khác, từ thư mục `self-storage`:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:DB_USERNAME = 'sa'
$env:DB_PASSWORD = '<mật khẩu SQL Server của bạn>'
$env:JWT_SECRET = '<chuỗi ngẫu nhiên bí mật, tối thiểu 32 ký tự>'
.\mvnw.cmd spring-boot:run
```

Mở `http://localhost:8080/stitch/ng_nh_p_ng_k_safebox_storage.html`. Nếu dùng cổng khác, đặt `$env:SERVER_PORT = '8081'` trước khi chạy. `APP_PUBLIC_BASE_URL` mặc định theo `localhost` và `SERVER_PORT`; khi truy cập qua tên máy khác hoặc proxy, đặt `$env:APP_PUBLIC_BASE_URL = 'http://ten-may:cong'` để liên kết trong email đúng địa chỉ.

Profile `local` dùng `src/main/resources/application-local.yml` (không chứa bí mật) để hiển thị liên kết nhỏ “Mở hộp thư demo” trên trang tài khoản. Các profile khác không hiển thị liên kết này. SMTP mặc định trỏ `localhost:1025`, không xác thực và không TLS. Để đổi sang SMTP thật về sau, đặt `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`, `SMTP_SSL`, `MAIL_FROM` qua biến môi trường; giữ bí mật ngoài Git.

## Kịch bản demo nhanh

1. Chọn **Đăng ký**, nhập tên, email, số điện thoại và mật khẩu tối thiểu 8 ký tự. Trang báo đăng ký thành công nhưng chưa đăng nhập. Thử đăng nhập ngay: hệ thống từ chối vì chưa xác thực.
2. Mở Mailpit tại `http://localhost:8025`, mở email “Xác thực tài khoản”, bấm nút hoặc liên kết văn bản. Trang chuyển về đăng nhập và báo xác thực thành công. Đăng nhập lại.
3. Để demo gửi lại, tạo một tài khoản khác, chọn **Gửi lại email xác thực**. Trong 60 giây đầu backend trả 429; sau đó email mới làm liên kết cũ mất hiệu lực. Liên kết xác thực hết hạn sau 30 phút và chỉ dùng một lần.
4. Chọn **Quên mật khẩu?**, nhập email, mở email “Đặt lại mật khẩu” trong Mailpit. Sau khi gửi, nút gửi lại chờ 60 giây theo giới hạn backend. Đặt mật khẩu mới và xác nhận, rồi đăng nhập bằng mật khẩu mới. Mật khẩu cũ và JWT được cấp trước khi đổi bị từ chối. Liên kết reset hết hạn sau 15 phút, chỉ dùng một lần và không dùng để xác thực email.
5. Tắt Mailpit rồi đăng ký bằng một email mới: API báo không gửi được email, tài khoản vẫn ở trạng thái chưa xác thực. Mở Mailpit lại và dùng **Gửi lại email xác thực** với email đó.

Token chỉ xuất hiện trong email. SQL Server chỉ lưu SHA-256 của token; không in hoặc commit liên kết/token demo. Dữ liệu demo dùng email `@safebox.test` để tránh gửi tới hộp thư thật.
