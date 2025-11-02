package ru.sup.userservice.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.sup.userservice.dto.EmailCode;

import java.util.concurrent.TimeUnit;

@Service
public class EmailEventProducer {

    private static final Logger log = LoggerFactory.getLogger(EmailEventProducer.class);
    private static final String TOPIC = "email-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EmailEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // === Методы отправки ===
    public void sendEmailCode(String email, String code) {
        EmailCode event = new EmailCode(email, code);
        sendEvent(event);
    }

    private void sendEvent(Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, "email.send_code", payload)
                    .get(5, TimeUnit.SECONDS); // Синхронно — ждём подтверждения

            log.info("Kafka event sent: key={}, topic={}, payload={}", "email.send_code", TOPIC, payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", event, e);
            throw new RuntimeException("Serialization failed", e);
        } catch (Exception e) {
            log.error("Failed to send Kafka event: {}", event, e);
            throw new RuntimeException("Kafka send failed", e);
        }
    }
}