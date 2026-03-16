package ru.sup.userservice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.sup.userservice.entity.Friendship;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.kafka.FriendshipEventProducer;
import ru.sup.userservice.repository.FriendshipRepository;
import ru.sup.userservice.repository.UserRepository;

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class FriendshipControllerV2IntegrationTest {

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

        @MockBean
        private FriendshipEventProducer friendshipEventProducer;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        friendshipRepository.deleteAll();
        userRepository.deleteAll();

        alice = new User();
        alice.setUsername("alice_it");
        alice.setPassword("$2a$pass");
        alice = userRepository.save(alice);

        bob = new User();
        bob.setUsername("bob_it");
        bob.setPassword("$2a$pass");
        bob = userRepository.save(bob);
    }

    @Test
    @WithMockUser(username = "alice_it")
    void sendRequest_thenOutgoingAndStatusReflectPending() throws Exception {
        mockMvc.perform(post("/api/v2/user/friends/{friendId}", bob.getId()).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/v2/user/friends/requests/outgoing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].addresseeId").value(bob.getId()));

        mockMvc.perform(get("/api/v2/user/friends/{friendId}/status", bob.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.isOutgoingRequest").value(true));
    }

    @Test
    void incomingRequests_visibleForAddressee() throws Exception {
        Friendship friendship = Friendship.builder()
                .requester(alice)
                .addressee(bob)
                .status(ru.sup.userservice.data.FriendshipStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        friendshipRepository.save(friendship);

        mockMvc.perform(get("/api/v2/user/friends/requests/incoming")
                        .with(user("bob_it")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].requesterId").value(alice.getId()));
    }

    @Test
    void acceptRequest_thenAreFriendsAndCount() throws Exception {
        mockMvc.perform(post("/api/v2/user/friends/{friendId}", bob.getId())
                        .with(user("alice_it"))
                        .with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v2/user/friends/{friendId}/accept", alice.getId())
                        .with(user("bob_it"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(get("/api/v2/user/friends/{friendId}/check", bob.getId())
                        .with(user("alice_it")))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        mockMvc.perform(get("/api/v2/user/friends/count")
                        .with(user("alice_it")))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @Test
    void unauthenticatedRequest_isRejected() throws Exception {
        mockMvc.perform(get("/api/v2/user/friends/count"))
                .andExpect(status().is4xxClientError());
    }
}
