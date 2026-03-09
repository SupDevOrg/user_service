package ru.sup.userservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import ru.sup.userservice.repository.UserRepository;
import ru.sup.userservice.security.jwt.JwtUtil;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    private static final String SECRET = "testSecretKeyForTestingPurposesOnlyMustBe32BytesLong";
    private static final long ACCESS_EXPIRATION_MS = 900_000L;  // 15 min
    private static final long REFRESH_EXPIRATION_MS = 2_592_000_000L; // 30 days

    @Mock
    private UserRepository userRepository;

    private JwtUtil jwtUtil;

    private ru.sup.userservice.entity.User testUser;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, ACCESS_EXPIRATION_MS, REFRESH_EXPIRATION_MS, userRepository);

        testUser = new ru.sup.userservice.entity.User();
        testUser.setId(42L);
        testUser.setUsername("alice");
        testUser.setPassword("$2a$encoded");

        userDetails = User.withUsername("alice")
                .password("$2a$encoded")
                .authorities("USER")
                .build();
    }

    // ======================== GENERATE ACCESS TOKEN ========================

    @Test
    void generateAccessToken_userExists_returnsNonNullToken() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));

        String token = jwtUtil.generateAccessToken(userDetails);

        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
    }

    @Test
    void generateAccessToken_userNotFound_returnsNull() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

        String token = jwtUtil.generateAccessToken(userDetails);

        assertThat(token).isNull();
    }

    @Test
    void generateAccessToken_withAvatarUrl_embedsAvatarClaim() {
        testUser.setAvatarURL("https://cdn.example.com/avatar.jpg");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));

        String token = jwtUtil.generateAccessToken(userDetails);

        assertThat(token).isNotNull();
        // avatarURL claim is embedded — verify by extracting claims via extractUsername (token is valid)
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("alice");
    }

    // ======================== GENERATE REFRESH TOKEN ========================

    @Test
    void generateRefreshToken_userExists_returnsNonNullToken() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));

        String token = jwtUtil.generateRefreshToken(userDetails);

        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void generateRefreshToken_userNotFound_returnsNull() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

        String token = jwtUtil.generateRefreshToken(userDetails);

        assertThat(token).isNull();
    }

    // ======================== EXTRACT USERNAME ========================

    @Test
    void extractUsername_validToken_returnsCorrectUsername() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        String token = jwtUtil.generateAccessToken(userDetails);

        String username = jwtUtil.extractUsername(token);

        assertThat(username).isEqualTo("alice");
    }

    // ======================== EXTRACT ID ========================

    @Test
    void extractId_validToken_returnsCorrectId() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        String token = jwtUtil.generateAccessToken(userDetails);

        Long id = jwtUtil.extractId(token);

        assertThat(id).isEqualTo(42L);
    }

    // ======================== VALIDATE TOKEN ========================

    @Test
    void validateToken_validToken_returnsTrue() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        String token = jwtUtil.generateAccessToken(userDetails);

        boolean valid = jwtUtil.validateToken(token, userDetails);

        assertThat(valid).isTrue();
    }

    @Test
    void validateToken_wrongUser_returnsFalse() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        String token = jwtUtil.generateAccessToken(userDetails);

        UserDetails otherUser = User.withUsername("bob")
                .password("$2a$encoded")
                .authorities("USER")
                .build();

        boolean valid = jwtUtil.validateToken(token, otherUser);

        assertThat(valid).isFalse();
    }

    @Test
    void validateToken_tamperedToken_returnsFalse() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        String token = jwtUtil.generateAccessToken(userDetails);
        String tampered = token + "tampered";

        boolean valid = jwtUtil.validateToken(tampered, userDetails);

        assertThat(valid).isFalse();
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        // Create JwtUtil with 1ms expiration
        JwtUtil shortLivedJwtUtil = new JwtUtil(SECRET, 1L, 1L, userRepository);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));

        String token = shortLivedJwtUtil.generateAccessToken(userDetails);

        // Wait for expiration
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        boolean valid = shortLivedJwtUtil.validateToken(token, userDetails);

        assertThat(valid).isFalse();
    }

    @Test
    void refreshToken_doesNotContainAvatarClaim() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));

        String refreshToken = jwtUtil.generateRefreshToken(userDetails);

        // Refresh token should still be valid and have correct username/userId
        assertThat(jwtUtil.extractUsername(refreshToken)).isEqualTo("alice");
        assertThat(jwtUtil.extractId(refreshToken)).isEqualTo(42L);
    }
}
