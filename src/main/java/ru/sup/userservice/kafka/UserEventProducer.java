package ru.sup.userservice.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.sup.userservice.dto.UserCreatedEvent;
import ru.sup.userservice.dto.UserUpdatedEvent;

import java.util.concurrent.TimeUnit;

@Service
public class UserEventProducer {

    private static final Logger log = LoggerFactory.getLogger(UserEventProducer.class);
    private static final String TOPIC = "user-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public UserEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // === Методы отправки ===
    public void sendUserCreated(Long userId, String username) {
        UserCreatedEvent event = new UserCreatedEvent(userId, username);
        sendEvent("user.created", event);
    }

    public void sendUserUpdated(Long userId, String field, String oldValue, String newValue) {
        UserUpdatedEvent event = new UserUpdatedEvent(userId, field, oldValue, newValue);
        sendEvent("user.updated", event);
    }

    private void sendEvent(String routingKey, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, routingKey, payload)
                    .get(5, TimeUnit.SECONDS); // Синхронно — ждём подтверждения

            log.info("Kafka event sent: key={}, topic={}, payload={}", routingKey, TOPIC, payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", event, e);
            throw new RuntimeException("Serialization failed", e);
        } catch (Exception e) {
            log.error("Failed to send Kafka event: {}", event, e);
            throw new RuntimeException("Kafka send failed", e);
        }
    }
}