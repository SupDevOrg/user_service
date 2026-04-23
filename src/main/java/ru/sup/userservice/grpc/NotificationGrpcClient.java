package ru.sup.userservice.grpc;

import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.sup.userservice.config.NotificationServiceProperties;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationGrpcClient {

    private final NotificationServiceGrpc.NotificationServiceBlockingStub stub;
    private final NotificationServiceProperties properties;

    public void sendNotification(long recipientId, long senderId, NotificationType type, Map<String, String> payload) {
        try {
            long createdAtUnixMs = Instant.now().toEpochMilli();
            log.info("Sending gRPC notification: type={}, recipientId={}, senderId={}, createdAtUnixMs={}",
                    type, recipientId, senderId, createdAtUnixMs);

            var request = SendNotificationRequest.newBuilder()
                    .setRecipientId(recipientId)
                    .setSenderId(senderId)
                    .setType(type)
                    .putAllPayload(payload)
                    .setCreatedAtUnixMs(createdAtUnixMs)
                    .build();

            var response = stub.withDeadlineAfter(properties.getDeadlineMs(), TimeUnit.MILLISECONDS)
                    .sendNotification(request);

            log.info("Notification sent: type={}, recipientId={}, success={}, message={}",
                    type, recipientId, response.getSuccess(), response.getMessage());

        } catch (StatusRuntimeException e) {
            log.warn("gRPC call failed [{}]: type={}, recipientId={}, reason={}",
                    e.getStatus().getCode(), type, recipientId, e.getStatus().getDescription());
        } catch (Exception e) {
            log.warn("Unexpected error sending notification: type={}, recipientId={}", type, recipientId, e);
        }
    }

    public void notifyFriendRequestReceived(long addresseeId, long requesterId) {
        sendNotification(addresseeId, requesterId, NotificationType.FRIEND_REQUEST_RECEIVED, Collections.emptyMap());
    }

    public void notifyFriendRequestAccepted(long originalRequesterId, long acceptorId) {
        sendNotification(originalRequesterId, acceptorId, NotificationType.FRIEND_REQUEST_ACCEPTED, Collections.emptyMap());
    }

    public void notifyFriendRequestRejected(long originalRequesterId, long rejecterId) {
        sendNotification(originalRequesterId, rejecterId, NotificationType.FRIEND_REQUEST_REJECTED, Collections.emptyMap());
    }
}
