package com.ldt.transaction.service;

import com.ldt.transaction.dto.TransactionResponse;
import com.ldt.transaction.dto.TransferRequest;
import com.ldt.transaction.dto.user.UserInternalResponse;
import com.ldt.transaction.mapper.TransactionMapper;
import com.ldt.transaction.model.Transaction;
import com.ldt.transaction.model.TransactionStatus;
import com.ldt.transaction.model.TransactionType;
import com.ldt.transaction.repository.TransactionRepository;
import com.ldt.wallet.grpc.WalletServiceGrpc;
import com.ldt.wallet.grpc.WalletTransferRequest;
import com.ldt.wallet.grpc.WalletTransferResponse;
import jakarta.transaction.Transactional;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final RestTemplate restTemplate;
    private final TransactionMapper transactionMapper;

    @GrpcClient("wallet-service")
    private WalletServiceGrpc.WalletServiceBlockingStub walletServiceStub;

    @Value("${user-service.url}")
    private String userServiceUrl;

    public TransactionService(TransactionRepository transactionRepository,
                              RestTemplate restTemplate,
                              TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.restTemplate = restTemplate;
        this.transactionMapper = transactionMapper;
    }

    @Transactional
    public TransactionResponse transfer(TransferRequest transferRequest) {
        // idempotency
        if (transactionRepository.existsByRequestId(transferRequest.getRequestId())) {
            throw new RuntimeException("Giao dịch đang được xử lí");
        }
        // Gọi user-service qua REST
        ResponseEntity<UserInternalResponse> responseFromUser =
                restTemplate.getForEntity(
                        userServiceUrl + "/internal/users/" + transferRequest.getFromPhoneNumber(),
                        UserInternalResponse.class
                );
        UserInternalResponse fromUser = responseFromUser.getBody();
        //
        ResponseEntity<UserInternalResponse> responseToUser =
                restTemplate.getForEntity(
                        userServiceUrl + "/internal/users/" + transferRequest.getToPhoneNumber(),
                        UserInternalResponse.class
                );
        UserInternalResponse toUser = responseToUser.getBody();
        //
        if (toUser.getStatus().equals("LOCKED")) {
            throw new RuntimeException("Ví bị khóa. Vui lòng nhập lại số điện thoại");
        }
        // Gọi wallet-service qua gRPC
        WalletTransferRequest walletReq = WalletTransferRequest.newBuilder()
                .setFromUserId(fromUser.getUserId().toString())
                .setToUserId(toUser.getUserId().toString())
                .setAmount(transferRequest.getAmount().toPlainString())
                .build();
        WalletTransferResponse walletResponse = walletServiceStub.transfer(walletReq);

        Transaction transaction = new Transaction();
        transaction.setRequestId(transferRequest.getRequestId());
        transaction.setFromWalletId(fromUser.getUserId());
        transaction.setToWalletId(toUser.getUserId());
        transaction.setAmount(transferRequest.getAmount());
        transaction.setDescription("Ví ID: " + fromUser.getUserId()
                + " -" + transferRequest.getAmount() + " -> "
                + "Ví ID: " + toUser.getUserId() + " +" + transferRequest.getAmount()
                + ", Nội dung: " + transferRequest.getDescription());
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction = transactionRepository.save(transaction);
        return transactionMapper.toTransactionResponse(transaction);
    }
}

