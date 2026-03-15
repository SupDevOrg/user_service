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
class EmailEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EmailEventProducer producer;

    @Test
    void sendEmailCode_success_sendsToKafka() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(kafkaTemplate.send(eq("email-auth-codes"), eq("email.send_code"), eq("{}")))
                .thenReturn((CompletableFuture) CompletableFuture.completedFuture(null));

        producer.sendEmailCode(1L, "alice@mail.com", "123456", "REGISTER");

        verify(kafkaTemplate).send("email-auth-codes", "email.send_code", "{}");
    }

    @Test
    void sendEmailCode_serializationError_throwsRuntimeException() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});

        assertThrows(RuntimeException.class,
                () -> producer.sendEmailCode(1L, "alice@mail.com", "123456", "REGISTER"));
    }
}
