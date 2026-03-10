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
import ru.sup.userservice.dto.UserDto;
import ru.sup.userservice.dto.request.VerificationEmailRequest;
import ru.sup.userservice.dto.response.SearchUsersResponse;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.security.CustomUserDetailsService;
import ru.sup.userservice.security.jwt.JwtTokenFilter;
import ru.sup.userservice.security.jwt.JwtUtil;
import ru.sup.userservice.service.UserService;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Import;
import ru.sup.userservice.config.SecurityConfig;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UtilsController.class)
@Import(SecurityConfig.class)
class UtilsControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
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

    // ======================== SEARCH USERS ========================

    @Test
    @WithMockUser
    void searchUsers_found_returns200WithResults() throws Exception {
        UserDto dto = new UserDto(1L, "alice", null);
        SearchUsersResponse response = new SearchUsersResponse(List.of(dto), 0, 1, 1);

        when(userService.searchUsersByUsernamePrefix("ali", 0, 10, "user")).thenReturn(response);

        mockMvc.perform(get("/api/v1/user/ali")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].username").value("alice"))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    @WithMockUser
    void searchUsers_emptyResult_returns404() throws Exception {
        SearchUsersResponse emptyResponse = new SearchUsersResponse(List.of(), 0, 0, 0);

        when(userService.searchUsersByUsernamePrefix("zzz", 0, 10, "user")).thenReturn(emptyResponse);

        mockMvc.perform(get("/api/v1/user/zzz")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void searchUsers_emptyPrefix_returns400() throws Exception {
        when(userService.searchUsersByUsernamePrefix(anyString(), anyInt(), anyInt(), anyString()))
                .thenThrow(new IllegalArgumentException("prefix cannot be empty"));

        mockMvc.perform(get("/api/v1/user/ "))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void searchUsers_serverError_returns500() throws Exception {
        when(userService.searchUsersByUsernamePrefix(anyString(), anyInt(), anyInt(), anyString()))
                .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/v1/user/ali"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void searchUsers_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/user/ali"))
                .andExpect(status().isForbidden());
    }

    // ======================== GET USER BY ID ========================

    @Test
    @WithMockUser
    void getUserById_found_returns200() throws Exception {
        UserDto dto = new UserDto(1L, "alice", "https://cdn.example.com/avatar.jpg");

        when(userService.getUserById(1L)).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/api/v1/user/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser
    void getUserById_notFound_returns404() throws Exception {
        when(userService.getUserById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/user/id/99"))
                .andExpect(status().isNotFound());
    }

    // ======================== VERIFY EMAIL ========================

    @Test
    @WithMockUser(username = "alice")
    void verifyEmail_correctCode_returns200() throws Exception {
        VerificationEmailRequest request = new VerificationEmailRequest();
        request.setCode("ABC123");

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userService.verifyEmail(user, "ABC123")).thenReturn(0);

        mockMvc.perform(post("/api/v1/user/verifyEmail").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "alice")
    void verifyEmail_wrongCode_returns406() throws Exception {
        VerificationEmailRequest request = new VerificationEmailRequest();
        request.setCode("WRONG1");

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userService.verifyEmail(user, "WRONG1")).thenReturn(1);

        mockMvc.perform(post("/api/v1/user/verifyEmail").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    @WithMockUser(username = "alice")
    void verifyEmail_noActiveCode_returns500() throws Exception {
        VerificationEmailRequest request = new VerificationEmailRequest();
        request.setCode("ABC123");

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userService.verifyEmail(user, "ABC123")).thenReturn(2);

        mockMvc.perform(post("/api/v1/user/verifyEmail").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void verifyEmail_unauthenticated_returns403() throws Exception {
        VerificationEmailRequest request = new VerificationEmailRequest();
        request.setCode("ABC123");

        mockMvc.perform(post("/api/v1/user/verifyEmail").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
