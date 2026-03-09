package ru.sup.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import ru.sup.userservice.config.SecurityConfig;
import ru.sup.userservice.entity.RefreshToken;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.repository.RefreshTokenRepository;
import ru.sup.userservice.security.CustomUserDetailsService;
import ru.sup.userservice.security.jwt.JwtTokenFilter;
import ru.sup.userservice.security.jwt.JwtUtil;
import ru.sup.userservice.service.UserService;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TokenController.class)
@Import(SecurityConfig.class)
class TokenControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
    @MockBean RefreshTokenRepository refreshTokenRepository;
    @MockBean JwtUtil jwtUtil;
    @MockBean CustomUserDetailsService customUserDetailsService;
    @MockBean JwtTokenFilter jwtTokenFilter;

    @BeforeEach
    void setupFilter() throws Exception {
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2)).doFilter(
                    (HttpServletRequest) inv.getArgument(0),
                    (HttpServletResponse) inv.getArgument(1));
            return null;
        }).when(jwtTokenFilter).doFilter(any(), any(), any());
    }

    // ======================== REFRESH TOKEN ========================

    @Test
    void refresh_validToken_returns200WithNewAccessToken() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        RefreshToken storedToken = RefreshToken.builder()
                .token("valid.refresh.token")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken("valid.refresh.token")).thenReturn(Optional.of(storedToken));
        when(userService.buildUserDetails(user)).thenReturn(
                org.springframework.security.core.userdetails.User
                        .withUsername("alice").password("pass").authorities("USER").build()
        );
        when(jwtUtil.generateAccessToken(any())).thenReturn("new.access.token");

        mockMvc.perform(post("/api/v1/user/refresh").with(csrf())
                        .param("refreshToken", "valid.refresh.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.access.token"))
                .andExpect(jsonPath("$.refreshToken").value("valid.refresh.token"));
    }

    @Test
    void refresh_expiredToken_returns200WithNewPair() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        RefreshToken expiredToken = RefreshToken.builder()
                .token("expired.refresh.token")
                .user(user)
                .expiryDate(Instant.now().minusSeconds(100))
                .revoked(false)
                .build();

        RefreshToken newToken = RefreshToken.builder()
                .token("new.refresh.token")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername("alice").password("pass").authorities("USER").build();

        when(refreshTokenRepository.findByToken("expired.refresh.token")).thenReturn(Optional.of(expiredToken));
        when(refreshTokenRepository.save(any())).thenReturn(expiredToken);
        when(userService.buildUserDetails(user)).thenReturn(userDetails);
        when(userService.createAndSaveRefreshToken(eq(user), any())).thenReturn(newToken);
        when(jwtUtil.generateAccessToken(any())).thenReturn("new.access.token");

        mockMvc.perform(post("/api/v1/user/refresh").with(csrf())
                        .param("refreshToken", "expired.refresh.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.access.token"))
                .andExpect(jsonPath("$.refreshToken").value("new.refresh.token"));

        verify(refreshTokenRepository).save(argThat(RefreshToken::isRevoked));
    }

    @Test
    void refresh_tokenNotFound_throwsException() {
        when(refreshTokenRepository.findByToken("unknown.token")).thenReturn(Optional.empty());

        assertThrows(Exception.class, () ->
                mockMvc.perform(post("/api/v1/user/refresh").with(csrf())
                        .param("refreshToken", "unknown.token")));
    }

    @Test
    void refresh_revokedToken_throwsException() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        RefreshToken revokedToken = RefreshToken.builder()
                .token("revoked.refresh.token")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByToken("revoked.refresh.token")).thenReturn(Optional.of(revokedToken));

        assertThrows(Exception.class, () ->
                mockMvc.perform(post("/api/v1/user/refresh").with(csrf())
                        .param("refreshToken", "revoked.refresh.token")));
    }

    @Test
    void refresh_emptyRefreshToken_throwsException() {
        assertThrows(Exception.class, () ->
                mockMvc.perform(post("/api/v1/user/refresh").with(csrf())
                        .param("refreshToken", "")));
    }

    @Test
    void refresh_noRefreshToken_throwsException() {
        assertThrows(Exception.class, () ->
                mockMvc.perform(post("/api/v1/user/refresh").with(csrf())));
    }
}
