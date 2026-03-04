package com.ldt.transaction.config.grpc;

import io.grpc.*;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@GrpcGlobalClientInterceptor
public class GrpcClientAuthInterceptor implements ClientInterceptor {

    @Value("${internal.secret}")
    private String internalKey;

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                Metadata.Key<String> key =
                        Metadata.Key.of("X-internal-key", Metadata.ASCII_STRING_MARSHALLER);
                headers.put(key, internalKey);
                super.start(responseListener, headers);
            }
        };
    }
}