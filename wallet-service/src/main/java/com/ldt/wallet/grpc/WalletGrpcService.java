package com.ldt.wallet.grpc;

import com.ldt.wallet.service.WalletService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class WalletGrpcService extends WalletServiceGrpc.WalletServiceImplBase {

    private final WalletService walletService;

    @Override
    public void transfer(WalletTransferRequest request, StreamObserver<WalletTransferResponse> responseObserver) {
        try {
            // Convert gRPC request to internal DTO
            com.ldt.wallet.dto.request.WalletTransferRequest walletTransferRequest = 
                new com.ldt.wallet.dto.request.WalletTransferRequest();
            
            walletTransferRequest.setFromUserId(UUID.fromString(request.getFromUserId()));
            walletTransferRequest.setToUserId(UUID.fromString(request.getToUserId()));
            walletTransferRequest.setAmount(new BigDecimal(request.getAmount()));

            // Call business logic
            String result = walletService.transfer(walletTransferRequest);

            // Build gRPC response
            WalletTransferResponse response = WalletTransferResponse.newBuilder()
                    .setStatus(result)
                    .build();

            // Send response back
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asException());
        }
    }
}
