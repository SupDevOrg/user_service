package ru.sup.userservice.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.sup.userservice.grpc.NotificationServiceGrpc;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class GrpcClientConfig {

    private final NotificationServiceProperties properties;

    private ManagedChannel channel;

    @Bean
    public ManagedChannel notificationManagedChannel() {
        channel = ManagedChannelBuilder
                .forAddress(properties.getHost(), properties.getPort())
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .build();
        return channel;
    }

    @Bean
    public NotificationServiceGrpc.NotificationServiceBlockingStub notificationServiceBlockingStub(
            ManagedChannel notificationManagedChannel) {
        return NotificationServiceGrpc.newBlockingStub(notificationManagedChannel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.warn("gRPC channel shutdown interrupted", e);
                Thread.currentThread().interrupt();
            }
        }
    }
}
