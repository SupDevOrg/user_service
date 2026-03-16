package ru.sup.userservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import ru.sup.userservice.dto.request.RegisterRequest;
import ru.sup.userservice.dto.request.UpdateRequest;
import ru.sup.userservice.dto.response.AvatarUploadUrlResponse;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.kafka.UserEventProducer;
import ru.sup.userservice.repository.FriendshipRepository;
import ru.sup.userservice.repository.RefreshTokenRepository;
import ru.sup.userservice.repository.UserRepository;
import ru.sup.userservice.repository.VerificationCodeRepository;
import ru.sup.userservice.service.AvatarStorageService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class UserControllerIntegrationTest {

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
    private ObjectMapper objectMapper;

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

        @MockBean
        private UserEventProducer userEventProducer;

    @MockBean AvatarStorageService avatarStorageService;

    private User seededUser;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        verificationCodeRepository.deleteAll();
        friendshipRepository.deleteAll();
        userRepository.deleteAll();

        seededUser = new User();
        seededUser.setUsername("alice_u");
        seededUser.setPassword(passwordEncoder.encode("pass123"));
        seededUser = userRepository.save(seededUser);
    }

    @Test
    void register_thenLogin_success() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("new_user");
        registerRequest.setPassword("new_pass");

        mockMvc.perform(post("/api/v1/user/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        mockMvc.perform(post("/api/v1/user/login")
                        .contentType("application/json")
                        .content("{\"username\":\"new_user\",\"password\":\"new_pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "alice_u")
    void updateUsername_authenticated_success() throws Exception {
        UpdateRequest updateRequest = new UpdateRequest();
        updateRequest.setUsername("alice_updated");

        mockMvc.perform(put("/api/v1/user/update").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        org.assertj.core.api.Assertions.assertThat(userRepository.findByUsername("alice_updated")).isPresent();
    }

    @Test
    @WithMockUser(username = "alice_u")
    void avatarUploadAndAccessUrl_flow() throws Exception {
        when(avatarStorageService.createAvatarUploadUrl(eq(seededUser.getId()), any(), any()))
                .thenReturn(new AvatarUploadUrlResponse(
                        "https://upload.url",
                        "https://storage.example/avatar.jpg",
                        "avatars/1/a.jpg",
                        900
                ));
        when(avatarStorageService.createAvatarAccessUrl("https://storage.example/avatar.jpg"))
                .thenReturn("https://access.url");
        when(avatarStorageService.getDownloadUrlExpirySeconds()).thenReturn(900);

        mockMvc.perform(post("/api/v1/user/avatar/upload-url").with(csrf())
                        .contentType("application/json")
                        .content("{\"contentType\":\"image/jpeg\",\"fileName\":\"a.jpg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://upload.url"));

        mockMvc.perform(get("/api/v1/user/avatar/access-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessUrl").value("https://access.url"));
    }
}
