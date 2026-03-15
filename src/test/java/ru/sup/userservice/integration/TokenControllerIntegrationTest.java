package ru.sup.userservice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.sup.userservice.entity.RefreshToken;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.repository.FriendshipRepository;
import ru.sup.userservice.repository.RefreshTokenRepository;
import ru.sup.userservice.repository.UserRepository;
import ru.sup.userservice.repository.VerificationCodeRepository;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class TokenControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        verificationCodeRepository.deleteAll();
        friendshipRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setUsername("alice_t");
        user.setPassword(passwordEncoder.encode("pass123"));
        user = userRepository.save(user);
    }

    @Test
    void refresh_validToken_returnsNewAccessAndSameRefresh() throws Exception {
        RefreshToken refreshToken = RefreshToken.builder()
                .token("valid.refresh.token")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        mockMvc.perform(post("/api/v1/user/refresh")
                        .param("refreshToken", "valid.refresh.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").value("valid.refresh.token"));
    }

    @Test
    void refresh_revokedToken_returns5xx() throws Exception {
        RefreshToken refreshToken = RefreshToken.builder()
                .token("revoked.refresh.token")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(true)
                .build();
        refreshTokenRepository.save(refreshToken);

        assertThrows(Exception.class, () ->
            mockMvc.perform(post("/api/v1/user/refresh")
                .param("refreshToken", "revoked.refresh.token")));
    }
}
