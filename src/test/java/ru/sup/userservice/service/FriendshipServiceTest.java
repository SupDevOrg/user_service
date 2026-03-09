package ru.sup.userservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import ru.sup.userservice.data.FriendshipStatus;
import ru.sup.userservice.dto.FriendshipDto;
import ru.sup.userservice.dto.FriendshipStatusDto;
import ru.sup.userservice.dto.UserDto;
import ru.sup.userservice.entity.Friendship;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.exception.BusinessException;
import ru.sup.userservice.exception.NotFoundException;
import ru.sup.userservice.kafka.FriendshipEventProducer;
import ru.sup.userservice.repository.FriendshipRepository;
import ru.sup.userservice.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    @Mock private FriendshipRepository friendshipRepository;
    @Mock private UserRepository userRepository;
    @Mock private FriendshipEventProducer friendshipEventProducer;

    @InjectMocks
    private FriendshipService friendshipService;

    // ======================== HELPERS ========================

    private User makeUser(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        return u;
    }

    private Friendship makeFriendship(Long id, User requester, User addressee, FriendshipStatus status) {
        return Friendship.builder()
                .id(id)
                .requester(requester)
                .addressee(addressee)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ======================== SEND FRIEND REQUEST ========================

    @Test
    void sendFriendRequest_success_returnsFriendshipDto() {
        User requester = makeUser(1L, "alice");
        User addressee = makeUser(2L, "bob");

        when(userRepository.existsById(1L)).thenReturn(true);
        when(userRepository.existsById(2L)).thenReturn(true);
        when(friendshipRepository.findByRequesterIdAndAddresseeId(1L, 2L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(requester);
        when(userRepository.getReferenceById(2L)).thenReturn(addressee);
        when(friendshipRepository.save(any())).thenAnswer(inv -> {
            Friendship f = inv.getArgument(0);
            Friendship saved = Friendship.builder()
                    .id(10L).requester(requester).addressee(addressee)
                    .status(FriendshipStatus.PENDING).createdAt(LocalDateTime.now()).build();
            return saved;
        });

        FriendshipDto result = friendshipService.sendFriendRequest(1L, 2L);

        assertThat(result.status()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(result.requesterId()).isEqualTo(1L);
        assertThat(result.addresseeId()).isEqualTo(2L);
        verify(friendshipEventProducer).sendFriendRequestSent(1L, 2L);
    }

    @Test
    void sendFriendRequest_selfRequest_throwsBusinessException() {
        when(userRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> friendshipService.sendFriendRequest(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("yourself");
    }

    @Test
    void sendFriendRequest_userNotFound_throwsNotFoundException() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> friendshipService.sendFriendRequest(1L, 99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void sendFriendRequest_alreadyPending_throwsBusinessException() {
        User requester = makeUser(1L, "alice");
        User addressee = makeUser(2L, "bob");
        Friendship existing = makeFriendship(1L, requester, addressee, FriendshipStatus.PENDING);

        when(userRepository.existsById(1L)).thenReturn(true);
        when(userRepository.existsById(2L)).thenReturn(true);
        when(friendshipRepository.findByRequesterIdAndAddresseeId(1L, 2L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> friendshipService.sendFriendRequest(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pending");
    }

    @Test
    void sendFriendRequest_alreadyFriends_throwsBusinessException() {
        User requester = makeUser(1L, "alice");
        User addressee = makeUser(2L, "bob");
        Friendship existing = makeFriendship(1L, requester, addressee, FriendshipStatus.ACCEPTED);

        when(userRepository.existsById(1L)).thenReturn(true);
        when(userRepository.existsById(2L)).thenReturn(true);
        when(friendshipRepository.findByRequesterIdAndAddresseeId(1L, 2L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> friendshipService.sendFriendRequest(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already friends");
    }

    @Test
    void sendFriendRequest_blockedStatus_throwsBusinessException() {
        User requester = makeUser(1L, "alice");
        User addressee = makeUser(2L, "bob");
        Friendship existing = makeFriendship(1L, requester, addressee, FriendshipStatus.BLOCKED);

        when(userRepository.existsById(1L)).thenReturn(true);
        when(userRepository.existsById(2L)).thenReturn(true);
        when(friendshipRepository.findByRequesterIdAndAddresseeId(1L, 2L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> friendshipService.sendFriendRequest(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("blocked");
    }

    // ======================== ACCEPT FRIEND REQUEST ========================

    @Test
    void acceptFriendRequest_success_returnsFriendshipDto() {
        User requester = makeUser(2L, "bob");     // friendId sent request
        User addressee = makeUser(1L, "alice");   // userId accepts
        Friendship pending = makeFriendship(5L, requester, addressee, FriendshipStatus.PENDING);

        when(friendshipRepository.findByRequesterIdAndAddresseeId(2L, 1L)).thenReturn(Optional.of(pending));
        when(friendshipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FriendshipDto result = friendshipService.acceptFriendRequest(1L, 2L);

        assertThat(result.status()).isEqualTo(FriendshipStatus.ACCEPTED);
        verify(friendshipEventProducer).sendFriendRequestAccepted(2L, 1L);
    }

    @Test
    void acceptFriendRequest_notFound_throwsNotFoundException() {
        when(friendshipRepository.findByRequesterIdAndAddresseeId(2L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendshipService.acceptFriendRequest(1L, 2L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void acceptFriendRequest_notPendingStatus_throwsNotFoundException() {
        User requester = makeUser(2L, "bob");
        User addressee = makeUser(1L, "alice");
        Friendship accepted = makeFriendship(5L, requester, addressee, FriendshipStatus.ACCEPTED);

        when(friendshipRepository.findByRequesterIdAndAddresseeId(2L, 1L)).thenReturn(Optional.of(accepted));

        assertThatThrownBy(() -> friendshipService.acceptFriendRequest(1L, 2L))
                .isInstanceOf(NotFoundException.class);
    }

    // ======================== REJECT FRIEND REQUEST ========================

    @Test
    void rejectFriendRequest_success_setsRejectedStatus() {
        User requester = makeUser(2L, "bob");
        User addressee = makeUser(1L, "alice");
        Friendship pending = makeFriendship(5L, requester, addressee, FriendshipStatus.PENDING);

        when(friendshipRepository.findByRequesterIdAndAddresseeId(2L, 1L)).thenReturn(Optional.of(pending));
        when(friendshipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        friendshipService.rejectFriendRequest(1L, 2L);

        assertThat(pending.getStatus()).isEqualTo(FriendshipStatus.REJECTED);
        verify(friendshipEventProducer).sendFriendRequestRejected(1L, 2L);
    }

    @Test
    void rejectFriendRequest_notFound_throwsNotFoundException() {
        when(friendshipRepository.findByRequesterIdAndAddresseeId(2L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendshipService.rejectFriendRequest(1L, 2L))
                .isInstanceOf(NotFoundException.class);
    }

    // ======================== CANCEL FRIEND REQUEST ========================

    @Test
    void cancelFriendRequest_success_deletesFriendship() {
        User requester = makeUser(1L, "alice");
        User addressee = makeUser(2L, "bob");
        Friendship pending = makeFriendship(5L, requester, addressee, FriendshipStatus.PENDING);

        when(friendshipRepository.findByRequesterIdAndAddresseeId(1L, 2L)).thenReturn(Optional.of(pending));

        friendshipService.cancelFriendRequest(1L, 2L);

        verify(friendshipRepository).delete(pending);
        verify(friendshipEventProducer).sendFriendRequestCancelled(1L, 2L);
    }

    @Test
    void cancelFriendRequest_notFound_throwsNotFoundException() {
        when(friendshipRepository.findByRequesterIdAndAddresseeId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendshipService.cancelFriendRequest(1L, 2L))
                .isInstanceOf(NotFoundException.class);
    }

    // ======================== REMOVE FRIEND ========================

    @Test
    void removeFriend_success_deletesFriendship() {
        User u1 = makeUser(1L, "alice");
        User u2 = makeUser(2L, "bob");
        Friendship accepted = makeFriendship(5L, u1, u2, FriendshipStatus.ACCEPTED);

        when(friendshipRepository.findByUserPairAndStatusAccepted(1L, 2L)).thenReturn(Optional.of(accepted));

        friendshipService.removeFriend(1L, 2L);

        verify(friendshipRepository).delete(accepted);
        verify(friendshipEventProducer, times(2)).sendFriendRemoved(anyLong(), anyLong());
    }

    @Test
    void removeFriend_notFriends_throwsNotFoundException() {
        when(friendshipRepository.findByUserPairAndStatusAccepted(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendshipService.removeFriend(1L, 2L))
                .isInstanceOf(NotFoundException.class);
    }

    // ======================== BLOCK USER ========================

    @Test
    void blockUser_noExistingRelationship_createsBlockedFriendship() {
        User u1 = makeUser(1L, "alice");
        User u2 = makeUser(2L, "bob");

        when(friendshipRepository.findByUserPair(1L, 2L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(u1);
        when(userRepository.getReferenceById(2L)).thenReturn(u2);
        when(friendshipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        friendshipService.blockUser(1L, 2L);

        verify(friendshipRepository).save(argThat(f -> f.getStatus() == FriendshipStatus.BLOCKED));
        verify(friendshipEventProducer).sendUserBlocked(1L, 2L);
    }

    @Test
    void blockUser_existingRelationship_updatesStatusToBlocked() {
        User u1 = makeUser(1L, "alice");
        User u2 = makeUser(2L, "bob");
        Friendship existing = makeFriendship(5L, u1, u2, FriendshipStatus.ACCEPTED);

        when(friendshipRepository.findByUserPair(1L, 2L)).thenReturn(Optional.of(existing));
        when(friendshipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        friendshipService.blockUser(1L, 2L);

        assertThat(existing.getStatus()).isEqualTo(FriendshipStatus.BLOCKED);
        verify(friendshipRepository).save(existing);
    }

    // ======================== UNBLOCK USER ========================

    @Test
    void unblockUser_blocked_deletesFriendship() {
        User u1 = makeUser(1L, "alice");
        User u2 = makeUser(2L, "bob");
        Friendship blocked = makeFriendship(5L, u1, u2, FriendshipStatus.BLOCKED);

        when(friendshipRepository.findByUserPair(1L, 2L)).thenReturn(Optional.of(blocked));

        friendshipService.unblockUser(1L, 2L);

        verify(friendshipRepository).delete(blocked);
    }

    @Test
    void unblockUser_notBlocked_doesNotDelete() {
        User u1 = makeUser(1L, "alice");
        User u2 = makeUser(2L, "bob");
        Friendship accepted = makeFriendship(5L, u1, u2, FriendshipStatus.ACCEPTED);

        when(friendshipRepository.findByUserPair(1L, 2L)).thenReturn(Optional.of(accepted));

        friendshipService.unblockUser(1L, 2L);

        verify(friendshipRepository, never()).delete(any());
    }

    @Test
    void unblockUser_noRelationship_doesNothing() {
        when(friendshipRepository.findByUserPair(1L, 2L)).thenReturn(Optional.empty());

        friendshipService.unblockUser(1L, 2L);

        verify(friendshipRepository, never()).delete(any());
    }

    // ======================== GET FRIENDS LIST ========================

    @Test
    void getFriendsList_hasFriends_returnsUserDtos() {
        UserDto dto1 = new UserDto(2L, "bob", null);
        UserDto dto2 = new UserDto(3L, "charlie", null);

        when(friendshipRepository.findAcceptedFriendIds(1L)).thenReturn(List.of(2L, 3L));
        when(userRepository.findUserDtoByIds(List.of(2L, 3L))).thenReturn(List.of(dto1, dto2));

        List<UserDto> result = friendshipService.getFriendsList(1L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserDto::getUsername).containsExactly("bob", "charlie");
    }

    @Test
    void getFriendsList_noFriends_returnsEmpty() {
        when(friendshipRepository.findAcceptedFriendIds(1L)).thenReturn(List.of());

        List<UserDto> result = friendshipService.getFriendsList(1L);

        assertThat(result).isEmpty();
        verify(userRepository, never()).findUserDtoByIds(any());
    }

    // ======================== GET FRIENDS PAGE ========================

    @Test
    void getFriendsPage_returnsCorrectPage() {
        UserDto dto1 = new UserDto(2L, "bob", null);
        UserDto dto2 = new UserDto(3L, "charlie", null);

        when(friendshipRepository.findAcceptedFriendIds(1L)).thenReturn(List.of(2L, 3L));
        when(userRepository.findUserDtoByIds(List.of(2L, 3L))).thenReturn(List.of(dto1, dto2));

        Page<UserDto> page = friendshipService.getFriendsPage(1L, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    // ======================== INCOMING / OUTGOING REQUESTS ========================

    @Test
    void getIncomingRequests_returnsListOfFriendshipDtos() {
        User requester = makeUser(2L, "bob");
        User addressee = makeUser(1L, "alice");
        Friendship pending = makeFriendship(5L, requester, addressee, FriendshipStatus.PENDING);

        when(friendshipRepository.findByAddresseeIdAndStatus(1L, FriendshipStatus.PENDING))
                .thenReturn(List.of(pending));

        List<FriendshipDto> result = friendshipService.getIncomingRequests(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).requesterId()).isEqualTo(2L);
        assertThat(result.get(0).addresseeId()).isEqualTo(1L);
    }

    @Test
    void getOutgoingRequests_returnsListOfFriendshipDtos() {
        User requester = makeUser(1L, "alice");
        User addressee = makeUser(2L, "bob");
        Friendship pending = makeFriendship(5L, requester, addressee, FriendshipStatus.PENDING);

        when(friendshipRepository.findByRequesterIdAndStatus(1L, FriendshipStatus.PENDING))
                .thenReturn(List.of(pending));

        List<FriendshipDto> result = friendshipService.getOutgoingRequests(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).requesterId()).isEqualTo(1L);
    }

    // ======================== FRIENDSHIP STATUS ========================

    @Test
    void getFriendshipStatus_sameUser_returnsAccepted() {
        FriendshipStatusDto result = friendshipService.getFriendshipStatus(1L, 1L);

        assertThat(result.status()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(result.isOutgoingRequest()).isTrue();
    }

    @Test
    void getFriendshipStatus_noRelationship_returnsNullStatus() {
        when(friendshipRepository.findByUserPair(1L, 2L)).thenReturn(Optional.empty());

        FriendshipStatusDto result = friendshipService.getFriendshipStatus(1L, 2L);

        assertThat(result.status()).isNull();
        assertThat(result.isOutgoingRequest()).isFalse();
    }

    @Test
    void getFriendshipStatus_pendingOutgoing_returnsCorrectDto() {
        User u1 = makeUser(1L, "alice");
        User u2 = makeUser(2L, "bob");
        Friendship pending = makeFriendship(5L, u1, u2, FriendshipStatus.PENDING);

        when(friendshipRepository.findByUserPair(1L, 2L)).thenReturn(Optional.of(pending));

        FriendshipStatusDto result = friendshipService.getFriendshipStatus(1L, 2L);

        assertThat(result.status()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(result.isOutgoingRequest()).isTrue(); // u1 is the requester = user1Id
    }

    @Test
    void getFriendshipStatus_pendingIncoming_isOutgoingFalse() {
        User u1 = makeUser(1L, "alice");
        User u2 = makeUser(2L, "bob");
        // u2 sent to u1 (u1 is addressee)
        Friendship pending = makeFriendship(5L, u2, u1, FriendshipStatus.PENDING);

        when(friendshipRepository.findByUserPair(1L, 2L)).thenReturn(Optional.of(pending));

        FriendshipStatusDto result = friendshipService.getFriendshipStatus(1L, 2L);

        assertThat(result.status()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(result.isOutgoingRequest()).isFalse(); // u2 is the requester, not u1
    }

    // ======================== ARE FRIENDS ========================

    @Test
    void areFriends_accepted_returnsTrue() {
        when(friendshipRepository.areFriends(1L, 2L)).thenReturn(true);

        boolean result = friendshipService.areFriends(1L, 2L);

        assertThat(result).isTrue();
    }

    @Test
    void areFriends_notFriends_returnsFalse() {
        when(friendshipRepository.areFriends(1L, 2L)).thenReturn(false);

        boolean result = friendshipService.areFriends(1L, 2L);

        assertThat(result).isFalse();
    }

    // ======================== GET FRIENDS COUNT ========================

    @Test
    void getFriendsCount_returnsCount() {
        when(friendshipRepository.countAcceptedFriends(1L)).thenReturn(5L);

        long count = friendshipService.getFriendsCount(1L);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    void getFriendsCount_noFriends_returnsZero() {
        when(friendshipRepository.countAcceptedFriends(1L)).thenReturn(0L);

        long count = friendshipService.getFriendsCount(1L);

        assertThat(count).isEqualTo(0L);
    }
}
