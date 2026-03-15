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
class FriendshipEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private FriendshipEventProducer producer;

    @Test
    void sendFriendRequestSent_success_sendsToKafka() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(kafkaTemplate.send(eq("friendship-events"), eq("friendship.request.sent"), eq("{}")))
                .thenReturn((CompletableFuture) CompletableFuture.completedFuture(null));

        producer.sendFriendRequestSent(1L, 2L);

        verify(kafkaTemplate).send("friendship-events", "friendship.request.sent", "{}");
    }

    @Test
    void sendFriendRequestAccepted_success_sendsToKafka() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(kafkaTemplate.send(eq("friendship-events"), eq("friendship.request.accepted"), eq("{}")))
                .thenReturn((CompletableFuture) CompletableFuture.completedFuture(null));

        producer.sendFriendRequestAccepted(1L, 2L);

        verify(kafkaTemplate).send("friendship-events", "friendship.request.accepted", "{}");
    }

    @Test
    void sendFriendRequestRejected_serializationError_throwsRuntimeException() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});

        assertThrows(RuntimeException.class,
                () -> producer.sendFriendRequestRejected(1L, 2L));
    }

    @Test
    void sendFriendRequestCancelled_sendError_isSwallowed() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(kafkaTemplate.send(eq("friendship-events"), eq("friendship.request.cancelled"), eq("{}")))
                .thenReturn((CompletableFuture) CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        producer.sendFriendRequestCancelled(1L, 2L);

        verify(kafkaTemplate).send("friendship-events", "friendship.request.cancelled", "{}");
    }

    @Test
    void sendFriendRemoved_andUserBlocked_success_sendsToKafka() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(kafkaTemplate.send(eq("friendship-events"), eq("friendship.removed"), eq("{}")))
                .thenReturn((CompletableFuture) CompletableFuture.completedFuture(null));
        when(kafkaTemplate.send(eq("friendship-events"), eq("friendship.user.blocked"), eq("{}")))
                .thenReturn((CompletableFuture) CompletableFuture.completedFuture(null));

        producer.sendFriendRemoved(1L, 2L);
        producer.sendUserBlocked(1L, 2L);

        verify(kafkaTemplate).send("friendship-events", "friendship.removed", "{}");
        verify(kafkaTemplate).send("friendship-events", "friendship.user.blocked", "{}");
    }
}
