# Spring Microservice gRPC Communication

Hệ thống microservice sử dụng gRPC để giao tiếp giữa các services

## Tổng Quan Kiến Trúc
┌─────────────────────┐
│ Transaction Service │
│  (gRPC Client)      │
│  Port: 8084   │
└──────────┬──────────┘
           │ gRPC call
           │ (plaintext)
           ↓
┌─────────────────────┐
│ Wallet Service      │
│ (gRPC Server)       │
│ Port: 8082, 9092    │
└─────────────────────┘

## Công Nghệ Sử Dụng
- gRPC: Protocol Buffers v3 để định nghĩa dịch vụ
- Spring Boot gRPC: 
    grpc-server-spring-boot-starter -> (wallet-service)
    grpc-client-spring-boot-starter -> (transaction-service)
- Protocol Buffers: Biên dịch .proto files thành Java code
- Spring Security: Internal secret key cho kết nối REST (user-service)

## Cấu Trúc Project
spring-microservice-gRPC/
├── grpc-contract/
│   └── transfer.proto              # Proto contract (không sử dụng)
│
├── transaction-service/            # Service xử lý giao dịch
│   ├── src/main/java/.../
│   │   ├── config/
│   │   │   └── RestTemplateConfig.java     # REST config (call user-service)
│   │   ├── controller/
│   │   ├── service/
│   │   │   └── TransactionService.java     # Gọi gRPC tới wallet-service
│   │   └── dto/
│   ├── src/main/proto/
│   │   └── transaction.proto       # Proto định nghĩa 
│   └── pom.xml
│
├── wallet-service/                 # Service quản lý ví
│   ├── src/main/java/.../
│   │   ├── grpc/
│   │   │   └── WalletServiceImpl.java       # gRPC Service Implementation
│   │   ├── service/
│   │   │   └── WalletService.java          # Business logic
│   │   ├── dto/
│   │   ├── model/
│   │   └── repository/
│   ├── src/main/proto/
│   │   └── wallet.proto            # Proto định nghĩa WalletService
│   ├── target/generated-sources/
│   │   └── protobuf/
│   │       ├── grpc-java/          # gRPC stub classes
│   │       └── java/               # Message classes
│   └── pom.xml
│
├── user-service/                   # Service quản lý user (REST)
│   └── pom.xml
│
└── README.md


## Luồng Giao Dịch Chi Tiết
1. Client POST /api/transactions/transfer
   ↓
2. TransactionService.transfer() [REST input]
   ├─ Validate requestId (idempotency)
   ├─ Call User Service (REST)
   │   GET /internal/users/{phoneNumber}
   │   + Header: X-Internal-Secret
   │   ← UserInternalResponse (userId, status)
   │
   └─ Call Wallet Service (gRPC)
       WalletServiceBlockingStub.transfer()
       ├─ Build WalletTransferRequest
       ├─ Send to localhost:9092
       ↓
3. WalletService Server (gRPC)
   └─ WalletServiceImpl.transfer()
      ├─ Validate amount > 0
      ├─ Check fromWallet exists & has balance
      ├─ Check toWallet exists
      ├─ Deduct from source, add to destination
      ├─ Save both wallets
      └─ Return WalletTransferResponse
       ↓
4. Transaction Service
   ├─ Receive WalletTransferResponse
   ├─ Save Transaction record
   └─ Return TransactionResponse