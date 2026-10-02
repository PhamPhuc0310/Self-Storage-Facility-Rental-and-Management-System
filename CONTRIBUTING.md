# Quy trình làm việc của team

Mỗi task có một Issue, một người phụ trách và điều kiện hoàn thành rõ ràng.
Project dùng các trạng thái: Todo, In progress, In review, Done.
Chỉ chuyển sang Done sau khi PR liên quan đã merge và điều kiện hoàn thành đạt.

## Dùng trên các máy và IDE khác nhau

NetBeans, IntelliJ và VS Code đều dùng cùng dự án Maven tại `self-storage/pom.xml`.
Mỗi máy cần JDK 21; dùng Maven Wrapper trong repo, không cần cài Maven riêng.
Windows dùng `mvnw.cmd`; macOS/Linux dùng `bash ./mvnw`.
Để chạy web, chuẩn bị SQL Server, chạy script trong `database/` và cấu hình DB
cho máy của mình. Clone repo không sao chép database đang chạy trên máy người khác.
Không sửa cấu hình chung thành đường dẫn máy cá nhân hoặc commit mật khẩu thật.

Mẫu Issue/PR và quy tắc review dùng được cho Java, JavaScript, HTML và tài liệu.
CI hiện dùng Maven theo công nghệ của repo. Nếu thêm dự án Node.js, Python hoặc
.NET, bổ sung job build/test riêng theo file cấu hình và dependency lockfile của
dự án đó; không coi Maven CI là kiểm tra được mọi ngôn ngữ.

## Pull Request

- PR vào `main`, tập trung vào một task; điền mẫu PR và liên kết Issue bằng `Closes #<số>`.
- Chạy `cd self-storage` rồi `bash ./mvnw verify` trước khi mở PR.
  Trên Windows dùng `.\mvnw.cmd verify`.
- Người khác trong team review. Sửa các nhận xét và chờ CI đạt trước khi merge.
- Dùng Squash and merge; tiêu đề cuối cùng mô tả thay đổi.
- Nếu sửa DB, kèm script cập nhật và hướng dẫn để team áp dụng.

## Commit

- `feat: thêm API đăng ký tài khoản`
- `fix: sửa chuyển trang sau đăng nhập`
- `test: bổ sung kiểm tra token hết hạn`
- `docs: bổ sung hướng dẫn chạy`
- `ci: cập nhật kiểm tra tự động`

## CI hiện tại

Workflow `CI` chạy Java 21 và Maven Wrapper trong `self-storage/`, bằng lệnh
`verify` để chạy test và đóng gói ứng dụng. Check bắt buộc dự kiến: `build-test`.
Các test hiện tại dùng mock cho repository/service và không cần SQL Server thật.
CI này chưa xác nhận kết nối SQL Server hoặc các luồng end-to-end trên giao diện.
Khi thêm integration test cần DB thật, bổ sung môi trường DB riêng cho CI.
