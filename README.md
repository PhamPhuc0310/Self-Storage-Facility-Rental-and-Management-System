# SafeBox Storage
## Self-Storage Facility Rental and Management System

SafeBox Storage là hệ thống hỗ trợ **cho thuê và quản lý kho tự quản (Self-Storage)** dành cho khách hàng cá nhân và doanh nghiệp.

Hệ thống hỗ trợ toàn bộ quá trình từ tìm kiếm kho, gửi yêu cầu đặt kho, xác nhận yêu cầu, ký hợp đồng, thanh toán, bàn giao kho, quản lý thời gian thuê, gia hạn, trả kho và xử lý các yêu cầu hỗ trợ.

> Dự án môn SWP – Software Project.

---

## 1. Mục tiêu dự án

SafeBox Storage được xây dựng nhằm mô phỏng quy trình hoạt động của một doanh nghiệp self-storage thực tế.

Luồng tổng quát:

```text
Tìm cơ sở
    ↓
Chọn loại kho
    ↓
Xem giá dự kiến
    ↓
Gửi yêu cầu đặt kho
    ↓
Nhân viên xác nhận / từ chối
    ↓
Ký hợp đồng
    ↓
Thanh toán
    ↓
Check-in / Bàn giao kho
    ↓
Quản lý thời gian thuê
    ↓
Gia hạn / Trả kho
    ↓
Kiểm tra kho
    ↓
Hoàn tất hợp đồng
```

Mục tiêu chính:

- Quản lý nhiều cơ sở lưu trữ.
- Quản lý nhiều loại kho và kho vật lý.
- Cho phép khách hàng tìm kho còn trống.
- Tính giá thuê dự kiến.
- Quản lý yêu cầu đặt kho.
- Quản lý hợp đồng thuê.
- Quản lý thanh toán.
- Quản lý check-in và bàn giao.
- Quản lý gia hạn và quá hạn.
- Quản lý trả kho.
- Quản lý hỗ trợ và bảo trì.
- Theo dõi hoạt động của hệ thống.

---

## 2. Công nghệ sử dụng

### Backend

- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA
- Spring Security
- Bean Validation
- Maven
- REST API

### Authentication

- Spring Security
- BCrypt
- JWT Authentication

### Database

- Microsoft SQL Server
- SQL Server Express
- JDBC
- Hibernate / JPA

Database chính:

```text
SelfStorageFacilityDB
```

### Development Tools

- IntelliJ IDEA, VS Code hoặc Eclipse / Spring Tools
- SQL Server Management Studio
- Git
- GitHub
- Postman / Swagger để test API

---

## 3. Cấu trúc Repository

```text
SWP/
│
├── database/
│   ├── SelfStorageFacilityDB.sql
│   └── ERD.drawio
│
├── docs/
│   └── tài liệu dự án
│
├── Self_Storage/
│   ├── AGENTS.md
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   │
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── com/safebox/storage/
│       │   │
│       │   └── resources/
│       │       ├── application.properties
│       │       └── application-dev.properties
│       │
│       └── test/
│
├── .gitignore
└── README.md
```

---

## 4. Cấu trúc Backend

Backend sử dụng kiến trúc:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
JPA / Hibernate
    ↓
SQL Server
```

Package:

```text
com.safebox.storage
│
├── controller
├── service
│   └── impl
├── repository
├── entity
├── dto
│   ├── request
│   └── response
├── security
├── config
├── exception
└── SelfStorageApplication.java
```

### Controller

Controller chịu trách nhiệm:

- Nhận HTTP Request.
- Validate request cơ bản.
- Gọi Service.
- Trả HTTP Response.

Controller không được:

- Viết SQL trực tiếp.
- Truy cập database trực tiếp.
- Chứa business logic lớn.

### Service

Service chịu trách nhiệm:

- Xử lý business logic.
- Kiểm tra business rule.
- Gọi Repository.
- Xử lý dữ liệu trước khi trả về Controller.

### Repository

Repository chịu trách nhiệm:

- Truy cập database.
- Query dữ liệu.
- Làm việc với Spring Data JPA.

### DTO

Không trả trực tiếp JPA Entity ra API.

Sử dụng:

```text
dto/request
dto/response
```

để trao đổi dữ liệu giữa client và backend.

---

## 5. Actor của hệ thống

### Storage Customer

Khách hàng có thể:

- Đăng nhập / đăng xuất.
- Xem danh sách cơ sở.
- Xem loại kho.
- Tìm kiếm và lọc kho.
- Xem kho còn trống.
- Xem giá thuê dự kiến.
- Gửi yêu cầu đặt kho.
- Xem yêu cầu đặt kho.
- Hủy yêu cầu khi còn được phép.
- Xem hợp đồng.
- Thanh toán.
- Xem kho đang thuê.
- Gia hạn.
- Yêu cầu trả kho.
- Gửi support request.

### Facility Staff

Nhân viên cơ sở có thể:

- Xem reservation request.
- Xác nhận hoặc từ chối reservation.
- Kiểm tra thông tin khách hàng.
- Hỗ trợ check-in.
- Assign kho vật lý.
- Bàn giao kho.
- Ghi nhận tình trạng kho.
- Xử lý support request.
- Hỗ trợ quá trình trả kho.

### Facility Manager

Quản lý cơ sở có thể:

- Quản lý facility.
- Quản lý storage unit.
- Quản lý storage unit type.
- Theo dõi trạng thái kho.
- Quản lý nhân viên của facility.

### Business Operations Manager

Có thể:

- Quản lý giá thuê.
- Quản lý phí.
- Quản lý rental policy.
- Theo dõi hoạt động kinh doanh.
- Theo dõi doanh thu.

### System Administrator

Có thể:

- Quản lý tài khoản.
- Quản lý role.
- Quản trị hệ thống.
- Theo dõi activity log.

---

## 6. Các luồng nghiệp vụ chính

### Flow 1 – Storage Unit Reservation Flow

```text
Customer
→ View Facilities
→ Select Storage Type
→ Select Rental Period
→ View Estimated Price
→ Submit Reservation
→ Staff Approve / Reject
```

### Flow 2 – Storage Check-in and Handover Flow

```text
Approved Reservation
→ Contract
→ Payment
→ Check-in Appointment
→ Verify Customer
→ Assign Physical Unit
→ Handover
```

### Flow 3 – Rented Storage Unit Management Flow

```text
Active Contract
→ View Storage Unit
→ View Contract
→ View Payment
→ Request Support
```

### Flow 4 – Business Rules, Fee Management and Revenue Monitoring

Bao gồm:

- Monthly rental price.
- Deposit.
- Overdue fee.
- Late payment fee.
- Early termination fee.
- Discount.
- Revenue monitoring.

### Flow 5 – Facility Storage and Staff Management

Bao gồm:

- Facility.
- Storage Unit.
- Storage Unit Type.
- Staff Assignment.
- Unit Status.

### Flow 6 – Storage Renewal and Overdue Handling

Bao gồm:

- Renewal.
- Renewal deadline.
- Payment deadline.
- Overdue.
- Late payment.

### Flow 7 – Support Request and Issue Handling

Bao gồm:

- Unit Problem.
- Lock / Key.
- Access Code.
- Payment.
- Stored Items.
- Maintenance.
- Other Issues.

---

## 7. Database

Database schema nằm tại:

```text
database/SelfStorageFacilityDB.sql
```

Database:

```text
SelfStorageFacilityDB
```

Các bảng chính:

```text
ROLES
USERS
ACTIVITY_LOGS

FACILITIES
USER_FACILITY_ASSIGNMENTS
RENTAL_POLICIES

STORAGE_UNIT_TYPES
STORAGE_UNITS

COMMODITY_CATEGORIES
UNIT_TYPE_COMMODITIES

PRICING_POLICIES

RESERVATIONS

RENTAL_CONTRACTS
CONTRACT_COMMODITIES

RENEWALS
TERMINATION_REQUESTS

INVOICES
PAYMENT_TRANSACTIONS

HANDOVER_RECORDS
INSPECTION_REPORTS

MAINTENANCE_REQUESTS
SUPPORT_REQUESTS
```

### Quy tắc Database

Không tự ý:

- Đổi tên bảng.
- Đổi tên column.
- Đổi data type.
- Xóa constraint.
- Xóa foreign key.
- Thay relationship.
- Thêm bảng mới khi chưa thống nhất.

Các ID dạng:

```text
UNIQUEIDENTIFIER
```

trong SQL Server phải map thành:

```java
java.util.UUID
```

Không tự ý đổi sang:

```java
Long
```

Hibernate được cấu hình:

```properties
spring.jpa.hibernate.ddl-auto=none
```

để Hibernate không tự sửa database schema.

---

## 8. Setup môi trường

Máy development cần:

- JDK 21
- Một IDE bất kỳ hỗ trợ Maven, hoặc chỉ dùng terminal
- Microsoft SQL Server
- SQL Server Management Studio
- Git

Repo đã có Maven Wrapper trong `Self_Storage/`, nên không cần cài Maven riêng.
Mở thư mục `Self_Storage` như một Maven project (IntelliJ: Open `pom.xml`;
VS Code: Open Folder và dùng Java Extension Pack; Eclipse: Import Existing Maven Projects).
Mọi thành viên dùng **JDK 21** và chạy cùng lệnh Maven Wrapper bên dưới.

Kiểm tra Java:

```bash
java -version
```

Yêu cầu:

```text
Java 21
```

---

## 9. Setup Database

Mở SQL Server Management Studio.

Chạy:

```text
database/SelfStorageFacilityDB.sql
```

Script chịu trách nhiệm:

```text
Create Database
→ Create Tables
→ Create Constraints
→ Create Indexes
→ Insert Sample Data
```

Database name:

```text
SelfStorageFacilityDB
```

SQL Server development mặc định:

```text
Host: localhost
Port: 1433
Database: SelfStorageFacilityDB
```

---

## 10. Spring Boot Configuration

File:

```text
Self_Storage/src/main/resources/application.properties
```

Cấu hình chính:

```properties
spring.application.name=Self_Storage

spring.profiles.active=dev

spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.open-in-view=false
```

Development config:

```text
application-dev.properties
```

Ví dụ:

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=SelfStorageFacilityDB;encrypt=true;trustServerCertificate=true

spring.datasource.username=${DB_USERNAME:sa}
spring.datasource.password=${DB_PASSWORD}

spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
```

Nếu mỗi máy có SQL Server account khác nhau thì sử dụng:

```text
DB_USERNAME
DB_PASSWORD
```

thay vì sửa source code. Trên PowerShell, đặt biến cho phiên terminal đang chạy
(dùng tài khoản SQL Server trên máy của mình):

```powershell
$env:DB_USERNAME = "sa"
$env:DB_PASSWORD = "<mat-khau-SQL-Server-cua-ban>"
```

Nếu SQL Server của bạn không chạy ở `localhost:1433`, chỉnh URL qua
`spring.datasource.url` trong file local bị Git bỏ qua:
`Self_Storage/src/main/resources/application-local.properties`, rồi chạy với
`--spring.profiles.active=dev,local`. Không commit file local này.

---

## 11. Chạy Backend

Từ repository:

```powershell
cd Self_Storage
```

Compile:

```powershell
.\mvnw.cmd clean compile
```

Nếu thành công:

```text
BUILD SUCCESS
```

Run:

```powershell
.\mvnw.cmd spring-boot:run
```

Backend mặc định chạy tại:

```text
http://localhost:8080
```

Test:

```powershell
.\mvnw.cmd test
```

Trên macOS/Linux dùng `./mvnw verify` hoặc `./mvnw spring-boot:run`.
Maven Wrapper, `pom.xml` và SQL script được commit để các IDE dùng chung bản build;
việc chạy ứng dụng vẫn cần SQL Server và biến `DB_PASSWORD` ở mỗi máy.

---

## Quy trình Git và Pull Request

- `main`: bản ổn định để demo; `develop`: tích hợp Sprint.
- Mỗi task tạo `feature/<ten-task>` từ `develop`, push và mở PR vào `develop`.
- Cuối Sprint mở PR `develop` → `main` sau khi kiểm tra.
- PR vào `develop` hoặc `main` chạy GitHub Actions **Build and test** với JDK 21,
  từ thư mục `Self_Storage`. CI chạy unit test, không kết nối SQL Server thật.
- CodeRabbit tự review PR khi GitHub App đã được cài cho repo. File
  `.coderabbit.yaml` thêm `develop` vào danh sách nhánh được tự review.
  Draft PR được review khi chuyển sang Ready for review.

Thiết lập một lần trong GitHub sau khi workflow có một lượt chạy:

1. Cài CodeRabbit GitHub App, cấp quyền **chỉ repo này**; kiểm tra bot trên một PR.
2. Tạo nhánh `develop` từ `main` nếu chưa có.
3. Trong **Settings → Rules → Rulesets**, tạo ruleset cho `develop` và `main`:
   yêu cầu PR, yêu cầu status check **Build and test**, giải quyết hội thoại,
   chặn force push và xóa nhánh. Không yêu cầu check trước khi nó xuất hiện
   lần đầu trong Actions.
4. Khi hai nhánh đã được bảo vệ, mọi thay đổi đi qua PR; bot AI gợi ý sửa,
   còn status check của CI quyết định build/test có đạt hay không.

Ví dụ trên PowerShell:

```powershell
git switch develop
git pull origin develop
git switch -c feature/login-validation
# code và kiểm tra
git add .
git commit -m "feat: validate login request"
git push -u origin feature/login-validation
```

---

## 12. Authentication

Authentication sử dụng:

```text
Spring Security
+
BCrypt
+
JWT
```

Endpoint:

```http
POST /api/auth/login
```

Ví dụ request:

```json
{
  "email": "customer1@safebox.vn",
  "password": "123456"
}
```

Response dự kiến:

```json
{
  "token": "JWT_TOKEN",
  "user": {
    "userId": "UUID",
    "fullName": "Nguyen Van A",
    "email": "customer1@safebox.vn",
    "role": "CUSTOMER"
  }
}
```

Role:

```text
CUSTOMER
FACILITY_STAFF
FACILITY_MANAGER
BUSINESS_OPERATIONS_MANAGER
SYSTEM_ADMIN
```

User chỉ được đăng nhập khi:

```text
status = ACTIVE
```

Password phải được lưu dưới dạng BCrypt hash.

Không lưu plain-text password.

---

## 13. Git Workflow

Branch ổn định:

```text
main
```

Không code trực tiếp trên `main`.

Mỗi chức năng phải tạo branch riêng.

### Feature

```text
feature/<feature-name>
```

Ví dụ:

```text
feature/auth-login
feature/facility-list
feature/storage-search
feature/reservation
feature/reservation-approval
```

### Bug Fix

```text
fix/<bug-name>
```

Ví dụ:

```text
fix/login-invalid-password
fix/reservation-status
```

### Refactor

```text
refactor/<name>
```

---

## 14. Quy trình làm việc với Git

Trước khi bắt đầu task:

```bash
git checkout main
git pull origin main
```

Tạo branch:

```bash
git checkout -b feature/auth-login
```

Sau khi code:

```bash
git status
git add .
git commit -m "feat: implement login authentication"
```

Push:

```bash
git push -u origin feature/auth-login
```

Sau đó tạo Pull Request:

```text
feature branch
→ main
```

---

## 15. Quy tắc Commit

Format:

```text
type: description
```

### feat

Thêm chức năng:

```text
feat: implement login authentication
feat: add facility listing API
feat: add reservation API
```

### fix

Sửa lỗi:

```text
fix: correct password validation
fix: fix reservation price calculation
```

### refactor

```text
refactor: simplify authentication service
```

### docs

```text
docs: update README
docs: update API documentation
```

### chore

```text
chore: setup Spring Boot project
chore: configure SQL Server
```

### test

```text
test: add authentication tests
```

Không sử dụng commit message như:

```text
update
fix
abc
done
code
code moi
sua
sua lai
123
```

Một commit nên tập trung vào một thay đổi logic.

---

## 16. Quy tắc Merge

Không merge khi:

- Project không compile.
- Backend không start.
- API đang lỗi.
- Database mapping lỗi.
- Test đang fail.
- Merge conflict chưa xử lý.
- Chưa kiểm tra code thay đổi.

Trước khi merge:

```powershell
.\mvnw.cmd clean compile
```

Phải có:

```text
BUILD SUCCESS
```

Nếu có test:

```powershell
.\mvnw.cmd test
```

phải pass.

### Đồng bộ main trước khi merge

Ví dụ đang làm:

```text
feature/reservation
```

thì cập nhật:

```bash
git checkout main
git pull origin main

git checkout feature/reservation
git merge main
```

Nếu có conflict phải kiểm tra kỹ.

Không chọn:

```text
Accept Yours
```

hoặc:

```text
Accept Theirs
```

một cách ngẫu nhiên.

Sau khi resolve:

```bash
git add .
git commit -m "merge: sync main into reservation branch"
git push
```

---

## 17. File không được commit

Không commit:

```text
target/
.idea/
*.iml
.env
*.log
```

Không commit:

- Password cá nhân.
- Access token.
- GitHub token.
- Production JWT secret.
- API key.
- Secret key.

---

## 18. AI  Rules

Project có:

```text
Self_Storage/AGENTS.md
```

để quy định cách AI thay đổi source code.

Codex có thể hỗ trợ:

- Entity.
- Repository.
- DTO.
- Service.
- Controller.
- Security.
- Test.
- Debug.
- Boilerplate code.

Trước khi chấp nhận code do AI tạo:

```text
1. Đọc git diff
2. Kiểm tra database mapping
3. Compile project
4. Run project
5. Test API
6. Kiểm tra AI có sửa file ngoài task không
```

AI không được tự ý:

- Sửa database schema.
- Đổi architecture.
- Đổi UUID thành Long.
- Rename database table.
- Rename database column.
- Thêm framework không cần thiết.
- Rewrite module không liên quan.
- Làm thêm chức năng ngoài yêu cầu.

---

## 19.1. Sprint 1

Sprint 1 gồm:

| UC | Chức năng |
|---|---|
| UC01 | Đăng nhập và đăng xuất |
| UC02 | Xem danh sách cơ sở và kho |
| UC03 | Tìm kiếm và lọc kho còn trống |
| UC04 | Xem chi tiết kho và giá dự kiến |
| UC05 | Tạo yêu cầu đặt kho |
| UC06 | Xem và hủy yêu cầu đặt kho của tôi |
| UC07 | Quản lý cơ sở và kho vật lý cơ bản |
| UC08 | Xác nhận hoặc từ chối yêu cầu đặt kho |

---

## 20. Trạng thái hiện tại

Đã hoàn thành:

```text
[x] Git Repository
[x] Spring Boot initialization
[x] Java 21
[x] Maven
[x] SQL Server setup
[x] Spring Boot ↔ SQL Server connection
[x] Database schema
[x] ERD
[x] Sample facility data
[x] Sample storage units
[x] Sample pricing data
[x] Sample user data
[x] Backend package structure
[x] AGENTS.md
```

Đang thực hiện:

```text
[ ] Authentication / Login
```

Tiếp theo:

```text
[ ] Facility API
[ ] Storage Search API
[ ] Facility Detail API
[ ] Pricing API
[ ] Reservation API
[ ] Reservation Management
```

---

---

## 22. Quy trình hoàn thành một Task

```text
Nhận task
    ↓
Pull main mới nhất
    ↓
Tạo branch riêng
    ↓
Code
    ↓
Compile
    ↓
Run
    ↓
Test
    ↓
Check git diff
    ↓
Commit
    ↓
Push
    ↓
Pull Request
    ↓
Merge
```

---

## 23. Nguyên tắc phát triển

Ưu tiên:

```text
Đơn giản
↓
Dễ hiểu
↓
Đúng nghiệp vụ
↓
Dễ test
↓
Dễ merge
↓
Dễ bảo trì
```

Không cố tình làm kiến trúc phức tạp nếu project không cần.

Các thay đổi lớn liên quan tới:

- Database.
- Architecture.
- Business flow.
- Authentication.
- API convention.
- Security.

phải được thống nhất trước khi thực hiện.

---

## 24. Project Information

```text
Project:
Self-Storage Facility Rental and Management System

Product:
SafeBox Storage

Course:
SWP – Software Project

Backend:
Spring Boot

Programming Language:
Java 21

Database:
Microsoft SQL Server
```
