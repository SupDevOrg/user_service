package ru.sup.userservice.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.sup.userservice.dto.EmailCode;


@Service
public class EmailEventProducer {

    private static final Logger log = LoggerFactory.getLogger(EmailEventProducer.class);
    private static final String TOPIC = "email-auth-codes";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EmailEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // === Методы отправки ===
    public void sendEmailCode(Long userID, String email, String code, String type) {
        EmailCode event = new EmailCode(userID, email, code, type);
        sendEvent(event);
    }

    private void sendEvent(Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", event, e);
            throw new RuntimeException("Serialization failed", e);
        }
        kafkaTemplate.send(TOPIC, "email.send_code", payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send Kafka email event: topic={}", TOPIC, ex);
                    } else {
                        log.debug("Kafka email event sent: topic={}", TOPIC);
                    }
                });
    }
}