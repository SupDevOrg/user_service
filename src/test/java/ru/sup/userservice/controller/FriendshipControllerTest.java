package ru.sup.userservice.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import ru.sup.userservice.data.FriendshipStatus;
import ru.sup.userservice.dto.FriendshipDto;
import ru.sup.userservice.dto.FriendshipStatusDto;
import ru.sup.userservice.dto.UserDto;
import ru.sup.userservice.security.jwt.JwtTokenFilter;
import ru.sup.userservice.service.AvatarStorageService;
import ru.sup.userservice.service.FriendshipService;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.annotation.Import;
import ru.sup.userservice.config.SecurityConfig;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FriendshipController.class)
@Import(SecurityConfig.class)
class FriendshipControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean FriendshipService friendshipService;
    @MockBean AvatarStorageService avatarStorageService;
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

    private FriendshipDto sampleDto(FriendshipStatus status) {
        return new FriendshipDto(1L, 1L, 2L, status, LocalDateTime.now(), null);
    }

    // ======================== SEND FRIEND REQUEST ========================

    @Test
    @WithMockUser
    void sendFriendRequest_success_returns201() throws Exception {
        when(friendshipService.sendFriendRequest(1L, 2L)).thenReturn(sampleDto(FriendshipStatus.PENDING));

        mockMvc.perform(post("/api/v1/user/1/friends/2").with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void sendFriendRequest_unauthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/user/1/friends/2").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ======================== ACCEPT FRIEND REQUEST ========================

    @Test
    @WithMockUser
    void acceptFriendRequest_success_returns200() throws Exception {
        when(friendshipService.acceptFriendRequest(1L, 2L)).thenReturn(sampleDto(FriendshipStatus.ACCEPTED));

        mockMvc.perform(put("/api/v1/user/1/friends/2/accept").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    // ======================== REJECT FRIEND REQUEST ========================

    @Test
    @WithMockUser
    void rejectFriendRequest_success_returns204() throws Exception {
        doNothing().when(friendshipService).rejectFriendRequest(1L, 2L);

        mockMvc.perform(delete("/api/v1/user/1/friends/2/reject").with(csrf()))
                .andExpect(status().isNoContent());
    }

    // ======================== CANCEL FRIEND REQUEST ========================

    @Test
    @WithMockUser
    void cancelFriendRequest_success_returns204() throws Exception {
        doNothing().when(friendshipService).cancelFriendRequest(1L, 2L);

        mockMvc.perform(delete("/api/v1/user/1/friends/2/cancel").with(csrf()))
                .andExpect(status().isNoContent());
    }

    // ======================== REMOVE FRIEND ========================

    @Test
    @WithMockUser
    void removeFriend_success_returns204() throws Exception {
        doNothing().when(friendshipService).removeFriend(1L, 2L);

        mockMvc.perform(delete("/api/v1/user/1/friends/2").with(csrf()))
                .andExpect(status().isNoContent());
    }

    // ======================== BLOCK USER ========================

    @Test
    @WithMockUser
    void blockUser_success_returns204() throws Exception {
        doNothing().when(friendshipService).blockUser(1L, 2L);

        mockMvc.perform(post("/api/v1/user/1/friends/2/block").with(csrf()))
                .andExpect(status().isNoContent());
    }

    // ======================== UNBLOCK USER ========================

    @Test
    @WithMockUser
    void unblockUser_success_returns204() throws Exception {
        doNothing().when(friendshipService).unblockUser(1L, 2L);

        mockMvc.perform(delete("/api/v1/user/1/friends/2/block").with(csrf()))
                .andExpect(status().isNoContent());
    }

    // ======================== GET FRIENDS ========================

    @Test
    @WithMockUser
    void getFriends_success_returns200WithPage() throws Exception {
        UserDto dto = new UserDto(2L, "bob", "https://cdn.example.com/avatar.jpg");
        when(friendshipService.getFriendsPage(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));
        when(avatarStorageService.createAvatarAccessUrl("https://cdn.example.com/avatar.jpg"))
                .thenReturn("https://signed.example.com/avatar.jpg");

        mockMvc.perform(get("/api/v1/user/1/friends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("bob"))
                .andExpect(jsonPath("$.content[0].avatarURL").value("https://signed.example.com/avatar.jpg"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser
    void getFriends_emptyPage_returns200() throws Exception {
        when(friendshipService.getFriendsPage(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/user/1/friends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ======================== INCOMING REQUESTS ========================

    @Test
    @WithMockUser
    void getIncomingRequests_returns200WithList() throws Exception {
        when(friendshipService.getIncomingRequests(1L))
                .thenReturn(List.of(sampleDto(FriendshipStatus.PENDING)));

        mockMvc.perform(get("/api/v1/user/1/friends/requests/incoming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    // ======================== OUTGOING REQUESTS ========================

    @Test
    @WithMockUser
    void getOutgoingRequests_returns200WithList() throws Exception {
        when(friendshipService.getOutgoingRequests(1L))
                .thenReturn(List.of(sampleDto(FriendshipStatus.PENDING)));

        mockMvc.perform(get("/api/v1/user/1/friends/requests/outgoing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    // ======================== FRIENDSHIP STATUS ========================

    @Test
    @WithMockUser
    void getFriendshipStatus_returns200WithStatus() throws Exception {
        when(friendshipService.getFriendshipStatus(1L, 2L))
                .thenReturn(new FriendshipStatusDto(FriendshipStatus.ACCEPTED, false));

        mockMvc.perform(get("/api/v1/user/1/friends/2/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    // ======================== FRIENDS COUNT ========================

    @Test
    @WithMockUser
    void getFriendsCount_returns200WithCount() throws Exception {
        when(friendshipService.getFriendsCount(1L)).thenReturn(5L);

        mockMvc.perform(get("/api/v1/user/1/friends/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    // ======================== ARE FRIENDS CHECK ========================

    @Test
    @WithMockUser
    void areFriends_returns200WithBooleanTrue() throws Exception {
        when(friendshipService.areFriends(1L, 2L)).thenReturn(true);

        mockMvc.perform(get("/api/v1/user/1/friends/2/check"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @WithMockUser
    void areFriends_notFriends_returns200WithBooleanFalse() throws Exception {
        when(friendshipService.areFriends(1L, 2L)).thenReturn(false);

        mockMvc.perform(get("/api/v1/user/1/friends/2/check"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }
}
