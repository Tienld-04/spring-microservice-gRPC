# Spring Microservice gRPC Communication

Hệ thống microservice sử dụng gRPC để giao tiếp giữa các services.

## Tổng Quan Kiến Trúc

 Hệ thống bao gồm 3 microservices chính giao tiếp với nhau qua REST và gRPC, mỗi service sử dụng một database PostgreSQL riêng biệt.

```text
                               REST (Port 8081)
                        ┌───────────────────────────────┐
                        │                               ▼
┌─────────────────────┐ │                     ┌─────────────────────┐
│ Transaction Service │─┘                     │ User Service        │
│ (gRPC Client)       │                       │ (REST API)          │
│ Port: 8084          │                       │ Port: 8081          │
│ DB: PostgreSQL      │                       │ DB: PostgreSQL      │
└──────────┬──────────┘                       └─────────────────────┘
           │ 
           │ gRPC call
           │ (plaintext, Port 9092)
           │
           ▼
┌─────────────────────┐
│ Wallet Service      │
│ (gRPC Server)       │
│ Port: 8082          │
│ DB: PostgreSQL      │
└─────────────────────┘
```

## Công Nghệ Sử Dụng
- **Java 21** & **Spring Boot 3.5.11**
- **gRPC & Protocol Buffers v3** để định nghĩa dịch vụ
- **Spring Boot gRPC:** 
    - `grpc-server-spring-boot-starter` -> (wallet-service)
    - `grpc-client-spring-boot-starter` -> (transaction-service)
- **PostgreSQL:** Database hoạt động độc lập cho từng microservice.
- **MapStruct & Lombok:** Hỗ trợ ánh xạ DTO, giảm boilerplate code.

---

## Cấu Trúc Project
```text
spring-microservice-gRPC/
├── transaction-service/            # Service xử lý giao dịch (REST Port: 8084)
│   ├── src/main/java/.../
│   │   ├── config/
│   │   ├── controller/
│   │   ├── service/
│   │   │   └── TransactionService.java     # Gọi REST tới user-service & gRPC tới wallet-service
│   │   └── dto/
│   ├── src/main/resources/
│   │   └── application.yaml                # Cấu hình port (8084), database, gRPC client address (9092)
│   └── pom.xml
│
├── wallet-service/                 # Service quản lý ví (REST Port: 8082, gRPC Port: 9092)
│   ├── src/main/java/.../
│   │   ├── grpc/
│   │   │   └── WalletGrpcService.java      # gRPC Service Implementation (implements WalletServiceImplBase)
│   │   ├── service/
│   │   │   └── WalletService.java          # Business logic giao dịch
│   │   ├── dto/
│   │   ├── model/
│   │   └── repository/
│   ├── src/main/resources/
│   │   └── application.yaml                # Cấu hình port (8082), gRPC server port (9092), database
│   └── pom.xml
│
├── user-service/                   # Service quản lý user (REST Port: 8081)
│   ├── src/main/resources/
│   │   └── application.yaml                # Cấu hình port (8081), database
│   └── pom.xml
```

## Luồng Giao Dịch Chi Tiết
1. `Client` gửi yêu cầu `POST /api/transactions/transfer` tới cổng 8084.
   ↓
2. `TransactionService.transfer()` tiếp nhận [REST request]
   ├─ Kiểm tra tính duy nhất (idempotency - Tránh tạo giao dịch lặp lại) bằng cách validate `requestId`.
   ├─ Gọi `User Service` qua (REST GET - `http://localhost:8081/internal/users/{phoneNumber}`)
   │   + Lấy mã bí mật internal từ config nếu cần (Header X-Internal-Secret).
   │   ← Trả về thông tin `UserInternalResponse` (`userId`, `status`) cho cả người gửi và người gửi và tài khoản thụ hưởng.
   │
   ├─ Kiểm tra điều kiện ngưng kích hoạt: Ví bị khóa sẽ ném Exception.
   │
   └─ Khởi tạo gọi `Wallet Service` bằng `gRPC` client (kết nối `static://localhost:9092` - plaintext)
       Gọi `WalletServiceBlockingStub.transfer()`
       ├─ Tạo đối tượng `WalletTransferRequest` chứa `UUID` người gửi/nhận và `Amount`.
       ├─ Gửi request tới server gRPC.
       ↓
3. `Wallet Service` Server (Phía nhận gRPC)
   └─ Tiếp nhận ở `WalletGrpcService.transfer()`
      ├─ Map đối tượng từ thông điệp gRPC sang model riêng của wallet-service.
      ├─ Bàn giao cho class business `WalletService.transfer()` xử lý nghiệp vụ:
      │  ├─ Validate các điều kiện (số dư ví phải đủ, mức chuyển > 0).
      │  ├─ Cập nhật trừ tiền từ ví gửi và cộng tiền cho chuyển nhận.
      │  └─ Lưu lại trạng thái của cả hai ví vào CSDL `wallet_service`.
      └─ Xây dựng câu trả lời `WalletTransferResponse` và trả lại thông qua luồng gRPC `responseObserver`.
       ↓
4. Trở lại `Transaction Service` 
   ├─ Nhận lại `WalletTransferResponse`.
   ├─ Khởi tạo và ghi chú đầy đủ lại biên lai lịch sử giao dịch (thêm dữ liệu source, destination).
   ├─ Lưu toàn bộ nội dung của Transaction record xuống DB `transaction_service` (với status là SUCCESS).
   └─ Transform và respond `TransactionResponse` trở lại cho Client sử dụng REST.