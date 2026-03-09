package ru.sup.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.sup.userservice.kafka.EmailEventProducer;
import ru.sup.userservice.kafka.FriendshipEventProducer;
import ru.sup.userservice.kafka.UserEventProducer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class UserserviceApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    // Mock Kafka producers - no real Kafka needed for context load test
    @MockBean EmailEventProducer emailEventProducer;
    @MockBean UserEventProducer userEventProducer;
    @MockBean FriendshipEventProducer friendshipEventProducer;

    @Test
    void contextLoads() {
        // Verifies the full Spring application context starts successfully
        // with a real PostgreSQL (Testcontainers) and Redis (Testcontainers)
    }
}
