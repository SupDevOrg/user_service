package ru.sup.userservice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.entity.VerificationCode;
import ru.sup.userservice.repository.FriendshipRepository;
import ru.sup.userservice.repository.RefreshTokenRepository;
import ru.sup.userservice.repository.UserRepository;
import ru.sup.userservice.repository.VerificationCodeRepository;
import ru.sup.userservice.service.AvatarStorageService;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class UtilsControllerIntegrationTest {

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
    private VerificationCodeRepository verificationCodeRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean AvatarStorageService avatarStorageService;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        verificationCodeRepository.deleteAll();
        friendshipRepository.deleteAll();
        userRepository.deleteAll();

        alice = new User();
        alice.setUsername("alice_utils");
        alice.setPassword(passwordEncoder.encode("pass123"));
        alice.setEmail("alice@mail.com");
        alice.setEmailVerification(false);
        alice = userRepository.save(alice);

        bob = new User();
        bob.setUsername("bob_utils");
        bob.setPassword(passwordEncoder.encode("pass123"));
        bob.setAvatarURL("https://storage.example/bob.jpg");
        bob = userRepository.save(bob);
    }

    @Test
    @WithMockUser(username = "alice_utils")
    void getUserById_andSearchByPrefix_success() throws Exception {
        when(avatarStorageService.createAvatarAccessUrl("https://storage.example/bob.jpg"))
                .thenReturn("https://signed.example/bob.jpg");

        mockMvc.perform(get("/api/v1/user/id/{userId}", bob.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob_utils"))
                .andExpect(jsonPath("$.avatarURL").value("https://signed.example/bob.jpg"));

        mockMvc.perform(get("/api/v1/user/{prefix}", "bo").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].username").value("bob_utils"));
    }

    @Test
    @WithMockUser(username = "alice_utils")
    void verifyEmail_validCode_returns200() throws Exception {
        VerificationCode code = new VerificationCode();
        code.setUser(alice);
        code.setEmail(alice.getEmail());
        code.setCode("123456");
        code.setRevoked(false);
        verificationCodeRepository.save(code);

        mockMvc.perform(post("/api/v1/user/verifyEmail").with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk());
    }
}
