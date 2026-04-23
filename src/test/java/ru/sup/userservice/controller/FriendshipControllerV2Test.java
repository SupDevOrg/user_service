package ru.sup.userservice.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import ru.sup.userservice.config.SecurityConfig;
import ru.sup.userservice.data.FriendshipStatus;
import ru.sup.userservice.dto.FriendshipDto;
import ru.sup.userservice.dto.FriendshipStatusDto;
import ru.sup.userservice.dto.UserDto;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.security.CustomUserDetailsService;
import ru.sup.userservice.security.jwt.JwtTokenFilter;
import ru.sup.userservice.service.AvatarStorageService;
import ru.sup.userservice.service.FriendshipService;
import ru.sup.userservice.service.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FriendshipControllerV2.class)
@Import(SecurityConfig.class)
class FriendshipControllerV2Test {

    @Autowired MockMvc mockMvc;

    @MockBean FriendshipService friendshipService;
    @MockBean UserService userService;
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

    private FriendshipDto sampleDto(FriendshipStatus status) {
        return new FriendshipDto(1L, 1L, 2L, status, LocalDateTime.now(), null);
    }

    private void mockCurrentUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
    }

    @Test
    @WithMockUser(username = "alice")
    void sendFriendRequest_success_returns201WithLocation() throws Exception {
        mockCurrentUser();
        when(friendshipService.sendFriendRequest(1L, 2L)).thenReturn(sampleDto(FriendshipStatus.PENDING));

        mockMvc.perform(post("/api/v2/user/friends/2").with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v2/user/friends/2"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "alice")
    void acceptFriendRequest_success_returns200() throws Exception {
        mockCurrentUser();
        when(friendshipService.acceptFriendRequest(1L, 2L)).thenReturn(sampleDto(FriendshipStatus.ACCEPTED));

        mockMvc.perform(put("/api/v2/user/friends/2/accept").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @WithMockUser(username = "alice")
    void rejectFriendRequest_success_returns204() throws Exception {
        mockCurrentUser();
        doNothing().when(friendshipService).rejectFriendRequest(1L, 2L);

        mockMvc.perform(delete("/api/v2/user/friends/2/reject").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "alice")
    void cancelFriendRequest_success_returns204() throws Exception {
        mockCurrentUser();
        doNothing().when(friendshipService).cancelFriendRequest(1L, 2L);

        mockMvc.perform(delete("/api/v2/user/friends/2/cancel").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "alice")
    void removeFriend_success_returns204() throws Exception {
        mockCurrentUser();
        doNothing().when(friendshipService).removeFriend(1L, 2L);

        mockMvc.perform(delete("/api/v2/user/friends/2").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "alice")
    void blockAndUnblock_success_returns204() throws Exception {
        mockCurrentUser();
        doNothing().when(friendshipService).blockUser(1L, 2L);
        doNothing().when(friendshipService).unblockUser(1L, 2L);

        mockMvc.perform(post("/api/v2/user/friends/2/block").with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v2/user/friends/2/block").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "alice")
    void getFriends_withSortAndAvatarSign_returns200() throws Exception {
        mockCurrentUser();
        UserDto dto = new UserDto(2L, "bob", "https://cdn.example.com/bob.jpg");
        when(friendshipService.getFriendsPage(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));
        when(avatarStorageService.createAvatarAccessUrl("https://cdn.example.com/bob.jpg"))
                .thenReturn("https://signed.example.com/bob.jpg");

        mockMvc.perform(get("/api/v2/user/friends").param("sort", "username,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("bob"))
                .andExpect(jsonPath("$.content[0].avatarURL").value("https://signed.example.com/bob.jpg"));
    }

    @Test
    @WithMockUser(username = "alice")
    void getFriends_withoutSortAndBlankAvatar_returns200() throws Exception {
        mockCurrentUser();
        UserDto dto = new UserDto(2L, "bob", "   ");
        when(friendshipService.getFriendsPage(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v2/user/friends").param("sort", "username"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].avatarURL").value("   "));
    }

    @Test
    @WithMockUser(username = "alice")
    void getFriends_withoutSortAndNullAvatar_returns200() throws Exception {
        mockCurrentUser();
        UserDto dto = new UserDto(2L, "bob", null);
        when(friendshipService.getFriendsPage(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v2/user/friends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].avatarURL").doesNotExist());
    }

    @Test
    @WithMockUser(username = "alice")
    void getFriends_withAscSort_returns200() throws Exception {
        mockCurrentUser();
        UserDto dto = new UserDto(2L, "bob", "https://cdn.example.com/bob.jpg");
        when(friendshipService.getFriendsPage(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));
        when(avatarStorageService.createAvatarAccessUrl("https://cdn.example.com/bob.jpg"))
                .thenReturn("https://signed.example.com/bob.jpg");

        mockMvc.perform(get("/api/v2/user/friends").param("sort", "username,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("bob"));
    }

    @Test
    @WithMockUser(username = "alice")
    void getFriends_withEmptySort_returns200() throws Exception {
        mockCurrentUser();
        UserDto dto = new UserDto(2L, "bob", "https://cdn.example.com/bob.jpg");
        when(friendshipService.getFriendsPage(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));
        when(avatarStorageService.createAvatarAccessUrl("https://cdn.example.com/bob.jpg"))
                .thenReturn("https://signed.example.com/bob.jpg");

        mockMvc.perform(get("/api/v2/user/friends").param("sort", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("bob"));
    }

    @Test
    @WithMockUser(username = "alice")
    void incomingOutgoingStatusCountAndCheck_success_returns200() throws Exception {
        mockCurrentUser();
        when(friendshipService.getIncomingRequests(1L)).thenReturn(List.of(sampleDto(FriendshipStatus.PENDING)));
        when(friendshipService.getOutgoingRequests(1L)).thenReturn(List.of(sampleDto(FriendshipStatus.PENDING)));
        when(friendshipService.getFriendshipStatus(1L, 2L))
                .thenReturn(new FriendshipStatusDto(FriendshipStatus.ACCEPTED, false));
        when(friendshipService.getFriendsCount(1L)).thenReturn(3L);
        when(friendshipService.areFriends(1L, 2L)).thenReturn(true);

        mockMvc.perform(get("/api/v2/user/friends/requests/incoming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        mockMvc.perform(get("/api/v2/user/friends/requests/outgoing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        mockMvc.perform(get("/api/v2/user/friends/2/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(get("/api/v2/user/friends/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("3"));

        mockMvc.perform(get("/api/v2/user/friends/2/check"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @WithMockUser(username = "alice")
    void currentUserNotFound_returns401() throws Exception {
        when(userService.findByUsername("alice")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v2/user/friends/count"))
                .andExpect(status().isUnauthorized());
    }

        @Test
        @WithMockUser(username = "anonymousUser")
        void anonymousUsername_returns401() throws Exception {
                mockMvc.perform(get("/api/v2/user/friends/count"))
                                .andExpect(status().isUnauthorized());
        }

    @Test
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v2/user/friends/count"))
                .andExpect(status().isUnauthorized());
    }
}
