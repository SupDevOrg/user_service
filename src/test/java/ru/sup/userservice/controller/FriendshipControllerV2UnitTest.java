package ru.sup.userservice.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import ru.sup.userservice.service.AvatarStorageService;
import ru.sup.userservice.service.FriendshipService;
import ru.sup.userservice.service.UserService;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class FriendshipControllerV2UnitTest {

    private final FriendshipService friendshipService = mock(FriendshipService.class);
    private final UserService userService = mock(UserService.class);
    private final AvatarStorageService avatarStorageService = mock(AvatarStorageService.class);

    private final FriendshipControllerV2 controller =
            new FriendshipControllerV2(friendshipService, userService, avatarStorageService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserId_whenAuthenticationIsNull_throwsUsernameNotFound() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(null);
        SecurityContextHolder.setContext(context);

        assertThrows(UsernameNotFoundException.class, controller::getFriendsCount);
    }

    @Test
    void getCurrentUserId_whenAuthenticationNameIsNull_throwsUsernameNotFound() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new NullNameAuthentication());
        SecurityContextHolder.setContext(context);

        assertThrows(UsernameNotFoundException.class, controller::getFriendsCount);
    }

    private static final class NullNameAuthentication implements Authentication {
        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return java.util.List.of();
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getDetails() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return null;
        }

        @Override
        public boolean isAuthenticated() {
            return true;
        }

        @Override
        public void setAuthenticated(boolean isAuthenticated) {
            // no-op for test stub
        }

        @Override
        public String getName() {
            return null;
        }
    }
}
