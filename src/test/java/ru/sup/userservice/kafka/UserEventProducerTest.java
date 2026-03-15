package ru.sup.userservice.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private UserEventProducer producer;

    @Test
    void sendUserCreated_success_sendsToKafka() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(kafkaTemplate.send(eq("user-events"), eq("user.created"), eq("{}")))
                .thenReturn((CompletableFuture) CompletableFuture.completedFuture(null));

        producer.sendUserCreated(1L, "alice");

        verify(kafkaTemplate).send("user-events", "user.created", "{}");
    }

    @Test
    void sendUserUpdated_serializationError_throwsRuntimeException() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});

        assertThrows(RuntimeException.class,
                () -> producer.sendUserUpdated(1L, "username", "old", "new"));
    }
}
