package ru.sup.userservice.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.sup.userservice.dto.event.UserCreatedEvent;
import ru.sup.userservice.dto.event.UserUpdatedEvent;


@Slf4j
@Service
public class UserEventProducer {

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
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", event, e);
            throw new RuntimeException("Serialization failed", e);
        }
        kafkaTemplate.send(TOPIC, routingKey, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send Kafka event: key={}, topic={}", routingKey, TOPIC, ex);
                    } else {
                        log.debug("Kafka event sent: key={}, topic={}", routingKey, TOPIC);
                    }
                });
    }
}