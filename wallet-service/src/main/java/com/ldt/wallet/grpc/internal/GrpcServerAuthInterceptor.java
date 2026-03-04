package com.ldt.wallet.grpc.internal;

import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@GrpcGlobalServerInterceptor
public class GrpcServerAuthInterceptor implements ServerInterceptor {

    @Value("${internal.secret}")
    private String INTERNAL_KEY;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> serverCall,
            Metadata metadata,
            ServerCallHandler<ReqT, RespT> serverCallHandler) {

        String key = metadata.get(
                Metadata.Key.of("X-internal-key", Metadata.ASCII_STRING_MARSHALLER));

        if (!INTERNAL_KEY.equals(key)) {
            serverCall.close(Status.UNAUTHENTICATED.withDescription("Invalid key"), metadata);
            return new ServerCall.Listener<>() {
            };
        }

        return serverCallHandler.startCall(serverCall, metadata);
    }
}
