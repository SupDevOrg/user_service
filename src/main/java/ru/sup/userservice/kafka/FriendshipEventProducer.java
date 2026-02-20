package ru.sup.userservice.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.sup.userservice.data.FriendRequestAction;
import ru.sup.userservice.dto.event.FriendRequestEvent;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class FriendshipEventProducer {

    private static final String TOPIC = "user-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public FriendshipEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // === Методы отправки событий ===

    /**
     * Отправлен запрос в друзья
     */
    public void sendFriendRequestSent(Long senderId, Long recipientId) {
        FriendRequestEvent event = FriendRequestEvent.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .action(FriendRequestAction.REQUEST_SENT)
                .timestamp(LocalDateTime.now())
                .build();
        sendEvent("friendship.request.sent", event);
    }

    /**
     * Запрос принят
     */
    public void sendFriendRequestAccepted(Long senderId, Long recipientId) {
        FriendRequestEvent event = FriendRequestEvent.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .action(FriendRequestAction.REQUEST_ACCEPTED)
                .timestamp(LocalDateTime.now())
                .build();
        sendEvent("friendship.request.accepted", event);
    }

    /**
     * Запрос отклонён
     */
    public void sendFriendRequestRejected(Long senderId, Long recipientId) {
        FriendRequestEvent event = FriendRequestEvent.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .action(FriendRequestAction.REQUEST_REJECTED)
                .timestamp(LocalDateTime.now())
                .build();
        sendEvent("friendship.request.rejected", event);
    }

    /**
     * Запрос отменён отправителем
     */
    public void sendFriendRequestCancelled(Long senderId, Long recipientId) {
        FriendRequestEvent event = FriendRequestEvent.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .action(FriendRequestAction.REQUEST_CANCELLED)
                .timestamp(LocalDateTime.now())
                .build();
        sendEvent("friendship.request.cancelled", event);
    }

    /**
     * Дружба разорвана
     */
    public void sendFriendRemoved(Long userId, Long friendId) {
        FriendRequestEvent event = FriendRequestEvent.builder()
                .senderId(userId)
                .recipientId(friendId)
                .action(FriendRequestAction.FRIEND_REMOVED)
                .timestamp(LocalDateTime.now())
                .build();
        sendEvent("friendship.removed", event);
    }

    /**
     * Пользователь заблокирован
     */
    public void sendUserBlocked(Long blockerId, Long blockedId) {
        FriendRequestEvent event = FriendRequestEvent.builder()
                .senderId(blockerId)
                .recipientId(blockedId)
                .action(FriendRequestAction.USER_BLOCKED)
                .timestamp(LocalDateTime.now())
                .build();
        sendEvent("friendship.user.blocked", event);
    }

    // === Внутренний метод отправки ===

    private void sendEvent(String routingKey, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, routingKey, payload)
                    .get(5, TimeUnit.SECONDS); // Синхронно — ждём подтверждения

            log.info("Kafka event sent: key={}, topic={}, payload={}", routingKey, TOPIC, payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize friendship event: {}", event, e);
            throw new RuntimeException("Serialization failed", e);
        } catch (Exception e) {
            log.error("Failed to send Kafka friendship event: {}", event, e);
            // Не прерываем основной поток — событие не критично
            // throw new RuntimeException("Kafka send failed", e);
        }
    }
}