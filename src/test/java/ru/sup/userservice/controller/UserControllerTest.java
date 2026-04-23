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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import ru.sup.userservice.dto.request.LoginRequest;
import ru.sup.userservice.dto.request.RegisterRequest;
import ru.sup.userservice.dto.request.UpdateRequest;
import ru.sup.userservice.dto.request.AvatarUploadUrlRequest;
import ru.sup.userservice.dto.response.AuthResponse;
import ru.sup.userservice.dto.response.AvatarUploadUrlResponse;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.kafka.UserEventProducer;
import ru.sup.userservice.security.CustomUserDetailsService;
import ru.sup.userservice.security.jwt.JwtTokenFilter;
import ru.sup.userservice.service.AvatarStorageService;
import ru.sup.userservice.service.UserService;

import java.util.Optional;

import org.springframework.context.annotation.Import;
import ru.sup.userservice.config.SecurityConfig;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
    @MockBean UserEventProducer userEventProducer;
    @MockBean AvatarStorageService avatarStorageService;
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

    // ======================== REGISTER ========================

    @Test
    void register_success_returns200WithTokens() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("pass123");

        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setUsername("alice");

        when(userService.register(any())).thenReturn(new AuthResponse("access.token", "refresh.token"));
        when(userService.findByUsername("alice")).thenReturn(Optional.of(savedUser));

        mockMvc.perform(post("/api/v1/user/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh.token"));

        verify(userEventProducer).sendUserCreated(1L, "alice");
    }

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("pass");

        when(userService.register(any())).thenThrow(new IllegalArgumentException("уже существует"));

        mockMvc.perform(post("/api/v1/user/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_userNotFoundAfterSave_returns500() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("pass");

        when(userService.register(any())).thenReturn(new AuthResponse("token", "refresh"));
        when(userService.findByUsername("alice")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/user/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void register_unexpectedError_returns500() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("pass");

        when(userService.register(any())).thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(post("/api/v1/user/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    // ======================== LOGIN ========================

    @Test
    void login_success_returns200WithTokens() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("pass123");

        when(userService.login(any())).thenReturn(new AuthResponse("access.token", "refresh.token"));

        mockMvc.perform(post("/api/v1/user/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.token"));
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrong");

        when(userService.login(any())).thenThrow(new RuntimeException("bad credentials"));

        mockMvc.perform(post("/api/v1/user/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ======================== DELETE ========================

    @Test
    @WithMockUser(username = "alice")
    void delete_authenticated_returns200() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
        doNothing().when(userService).deleteUser(user);

        mockMvc.perform(delete("/api/v1/user/delete").with(csrf()))
                .andExpect(status().isOk());

        verify(userService).deleteUser(user);
    }

    @Test
    void delete_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/user/delete").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice")
    void delete_userNotFound_returns401() throws Exception {
        when(userService.findByUsername("alice")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/user/delete").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ======================== UPDATE ========================

    @Test
    @WithMockUser(username = "alice")
    void update_withUsername_returns200() throws Exception {
        UpdateRequest request = new UpdateRequest();
        request.setUsername("alice_new");

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userService.update(any(), any())).thenReturn(new AuthResponse("new.access", "new.refresh"));

        mockMvc.perform(put("/api/v1/user/update").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.access"));

        verify(userEventProducer).sendUserUpdated(eq(1L), eq("username"), eq("alice"), anyString());
    }

    @Test
    @WithMockUser(username = "alice")
    void update_noChanges_returns400() throws Exception {
        UpdateRequest request = new UpdateRequest();
        // all fields are null or blank

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));

        mockMvc.perform(put("/api/v1/user/update").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "alice")
    void update_blankFields_returns400() throws Exception {
        UpdateRequest request = new UpdateRequest();
        request.setUsername("   ");
        request.setPassword(" ");
        request.setEmail("\t");
        request.setPhone("  ");

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));

        mockMvc.perform(put("/api/v1/user/update").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "alice")
    void update_sameUsername_noUsernameChange() throws Exception {
        UpdateRequest request = new UpdateRequest();
        request.setUsername("alice"); // same as current → not a change
        request.setPassword("newPassword");

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userService.update(any(), any())).thenReturn(new AuthResponse("new.access", "new.refresh"));

        mockMvc.perform(put("/api/v1/user/update").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userEventProducer, never()).sendUserUpdated(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = "alice")
    void update_withEmailAndPhone_returns200() throws Exception {
        UpdateRequest request = new UpdateRequest();
        request.setEmail("alice@example.com");
        request.setPhone("+79998887766");

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userService.update(any(), any())).thenReturn(new AuthResponse("new.access", "new.refresh"));

        mockMvc.perform(put("/api/v1/user/update").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.access"));

        verify(userEventProducer, never()).sendUserUpdated(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = "alice")
    void update_userNotFound_returns401() throws Exception {
        UpdateRequest request = new UpdateRequest();
        request.setPassword("newPassword");

        when(userService.findByUsername("alice")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/user/update").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void update_unauthenticated_returns401() throws Exception {
        UpdateRequest request = new UpdateRequest();
        request.setUsername("alice_new");

        mockMvc.perform(put("/api/v1/user/update").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

        @Test
        @WithMockUser(username = "alice")
        void createAvatarUploadUrl_authenticated_returns200() throws Exception {
        AvatarUploadUrlRequest request = new AvatarUploadUrlRequest();
        request.setContentType("image/jpeg");
        request.setFileName("avatar.jpg");

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setAvatarURL("http://old/avatar.jpg");

        AvatarUploadUrlResponse response = new AvatarUploadUrlResponse(
            "https://presigned.put.url",
            "http://localhost:9000/avatars/avatars/1/new.jpg",
            "avatars/1/new.jpg",
            900
        );

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
        when(avatarStorageService.createAvatarUploadUrl(eq(1L), eq("image/jpeg"), eq("avatar.jpg")))
            .thenReturn(response);

        mockMvc.perform(post("/api/v1/user/avatar/upload-url").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uploadUrl").value("https://presigned.put.url"))
            .andExpect(jsonPath("$.avatarUrl").value("http://localhost:9000/avatars/avatars/1/new.jpg"));

        verify(userService).updateAvatarUrl(user, "http://localhost:9000/avatars/avatars/1/new.jpg");
        verify(userEventProducer).sendUserUpdated(1L, "avatarURL", "http://old/avatar.jpg", "http://localhost:9000/avatars/avatars/1/new.jpg");
        }

        @Test
        void createAvatarUploadUrl_unauthenticated_returns401() throws Exception {
        AvatarUploadUrlRequest request = new AvatarUploadUrlRequest();
        request.setContentType("image/jpeg");

        mockMvc.perform(post("/api/v1/user/avatar/upload-url").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
        }

    @Test
    @WithMockUser(username = "alice")
    void createAvatarAccessUrl_authenticated_returns200() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setAvatarURL("https://storage.example.com/avatars/avatars/1/new.jpg");

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
        when(avatarStorageService.createAvatarAccessUrl("https://storage.example.com/avatars/avatars/1/new.jpg"))
            .thenReturn("https://presigned.get.url");
        when(avatarStorageService.getDownloadUrlExpirySeconds()).thenReturn(900);

        mockMvc.perform(get("/api/v1/user/avatar/access-url").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessUrl").value("https://presigned.get.url"))
            .andExpect(jsonPath("$.expiresInSeconds").value(900));
    }

    @Test
    @WithMockUser(username = "alice")
    void createAvatarAccessUrl_withoutAvatar_returns404() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setAvatarURL("   ");

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/user/avatar/access-url").with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "alice")
    void createAvatarAccessUrl_withNullAvatar_returns404() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setAvatarURL(null);

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/user/avatar/access-url").with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "alice")
    void createAvatarUploadUrl_userNotFound_returns401() throws Exception {
        AvatarUploadUrlRequest request = new AvatarUploadUrlRequest();
        request.setContentType("image/jpeg");
        request.setFileName("avatar.jpg");

        when(userService.findByUsername("alice")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/user/avatar/upload-url").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice")
    void createAvatarAccessUrl_userNotFound_returns401() throws Exception {
        when(userService.findByUsername("alice")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/user/avatar/access-url").with(csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createAvatarAccessUrl_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/user/avatar/access-url").with(csrf()))
            .andExpect(status().isUnauthorized());
    }
}
