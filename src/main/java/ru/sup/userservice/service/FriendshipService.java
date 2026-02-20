package ru.sup.userservice.service;

import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageImpl;
import ru.sup.userservice.dto.FriendshipDto;
import ru.sup.userservice.dto.FriendshipStatusDto;
import ru.sup.userservice.dto.UserDto;
import ru.sup.userservice.entity.Friendship;
import ru.sup.userservice.data.FriendshipStatus;
import ru.sup.userservice.exception.BusinessException;
import ru.sup.userservice.exception.NotFoundException;
import ru.sup.userservice.kafka.FriendshipEventProducer;
import ru.sup.userservice.repository.FriendshipRepository;
import ru.sup.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final FriendshipEventProducer friendshipEventProducer;

    /**
     * Отправить запрос в друзья
     */
    @Transactional
    public FriendshipDto sendFriendRequest(Long requesterId, Long addresseeId) {
        validateUsersExist(requesterId, addresseeId);
        validateNotSelf(requesterId, addresseeId);
        validateNoActiveConnection(requesterId, addresseeId);

        var friendship = Friendship.builder()
                .requester(userRepository.getReferenceById(requesterId))
                .addressee(userRepository.getReferenceById(addresseeId))
                .status(FriendshipStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        var saved = friendshipRepository.save(friendship);
        log.info("Friend request sent: {} -> {}", requesterId, addresseeId);

        friendshipEventProducer.sendFriendRequestSent(requesterId, addresseeId);
        evictFriendCache(addresseeId);

        return FriendshipDto.from(saved);
    }

    /**
     * Принять запрос в друзья
     */
    @Transactional
    @CacheEvict(value = "userFriends", key = "#userId")
    public FriendshipDto acceptFriendRequest(Long userId, Long friendId) {
        var friendship = friendshipRepository
                .findByRequesterIdAndAddresseeId(friendId, userId)
                .filter(f -> f.getStatus() == FriendshipStatus.PENDING)
                .orElseThrow(() -> new NotFoundException("Friend request not found"));

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setUpdatedAt(LocalDateTime.now());

        var saved = friendshipRepository.save(friendship);
        log.info("Friend request accepted: {} <-> {}", userId, friendId);

        friendshipEventProducer.sendFriendRequestAccepted(friendId, userId);
        evictFriendCache(userId);
        evictFriendCache(friendId);

        return FriendshipDto.from(saved);
    }

    /**
     * Отклонить запрос в друзья
     */
    @Transactional
    @CacheEvict(value = "userFriends", key = "#userId")
    public void rejectFriendRequest(Long userId, Long friendId) {
        var friendship = friendshipRepository
                .findByRequesterIdAndAddresseeId(friendId, userId)
                .filter(f -> f.getStatus() == FriendshipStatus.PENDING)
                .orElseThrow(() -> new NotFoundException("Friend request not found"));

        friendship.setStatus(FriendshipStatus.REJECTED);
        friendship.setUpdatedAt(LocalDateTime.now());
        friendshipRepository.save(friendship);

        log.info("Friend request rejected: {} -/-> {}", userId, friendId);
        evictFriendCache(userId);
    }

    /**
     * Отменить исходящий запрос
     */
    @Transactional
    @CacheEvict(value = "userFriends", key = "#friendId")
    public void cancelFriendRequest(Long requesterId, Long addresseeId) {
        var friendship = friendshipRepository
                .findByRequesterIdAndAddresseeId(requesterId, addresseeId)
                .filter(f -> f.getStatus() == FriendshipStatus.PENDING)
                .orElseThrow(() -> new NotFoundException("Friend request not found"));

        friendshipRepository.delete(friendship);
        log.info("Friend request cancelled: {} -/-> {}", requesterId, addresseeId);
        evictFriendCache(addresseeId);

        friendshipEventProducer.sendFriendRequestCancelled(requesterId, addresseeId);
    }

    /**
     * Удалить друга (разорвать связь)
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "userFriends", key = "#userId"),
            @CacheEvict(value = "userFriends", key = "#friendId")
    })
    public void removeFriend(Long userId, Long friendId) {
        var friendship = findActiveFriendship(userId, friendId)
                .orElseThrow(() -> new NotFoundException("Friendship not found"));

        friendshipRepository.delete(friendship);
        log.info("Friendship removed: {} -/-> {}", userId, friendId);

        evictFriendCache(userId);
        evictFriendCache(friendId);

        friendshipEventProducer.sendFriendRemoved(userId, friendId);
        friendshipEventProducer.sendFriendRemoved(friendId, userId);
    }

    /**
     * Заблокировать пользователя (без удаления истории)
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "userFriends", key = "#userId"),
            @CacheEvict(value = "userFriends", key = "#friendId")
    })
    public void blockUser(Long userId, Long targetId) {
        var friendship = findAnyFriendship(userId, targetId);

        if (friendship == null) {
            friendship = Friendship.builder()
                    .requester(userRepository.getReferenceById(userId))
                    .addressee(userRepository.getReferenceById(targetId))
                    .status(FriendshipStatus.BLOCKED)
                    .createdAt(LocalDateTime.now())
                    .build();
        } else {
            friendship.setStatus(FriendshipStatus.BLOCKED);
            friendship.setUpdatedAt(LocalDateTime.now());
        }

        friendshipRepository.save(friendship);
        log.info("User blocked: {} -X-> {}", userId, targetId);

        evictFriendCache(userId);
        evictFriendCache(targetId);

        friendshipEventProducer.sendUserBlocked(userId, targetId);
    }

    /**
     * Разблокировать пользователя
     */
    @Transactional
    public void unblockUser(Long userId, Long targetId) {
        var friendship = findAnyFriendship(userId, targetId);
        if (friendship != null && friendship.getStatus() == FriendshipStatus.BLOCKED) {
            friendshipRepository.delete(friendship);
            log.info("User unblocked: {} -/X-> {}", userId, targetId);
            evictFriendCache(userId);
            evictFriendCache(targetId);
        }
    }

    /**
     * Получить список друзей с пагинацией
     */
    @Transactional(readOnly = true)
    public Page<UserDto> getFriendsPage(Long userId, Pageable pageable) {
        // Получаем полный список из кэша (или БД)
        var allFriends = getFriendsList(userId);

        // Применяем пагинацию в памяти (дешево для разумного числа друзей)
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allFriends.size());

        var pageContent = allFriends.subList(start, end);
        return new PageImpl<>(pageContent, pageable, allFriends.size());
    }

    @Cacheable(value = "userFriendsList", key = "#userId", unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public List<UserDto> getFriendsList(Long userId) {
        var friendIds = friendshipRepository.findAcceptedFriendIds(userId);
        return friendIds.isEmpty()
                ? List.of()
                : userRepository.findUserDtoByIds(friendIds);
    }

    /**
     * Получить входящие запросы на дружбу
     */
    @Transactional(readOnly = true)
    public List<FriendshipDto> getIncomingRequests(Long userId) {
        return friendshipRepository
                .findByAddresseeIdAndStatus(userId, FriendshipStatus.PENDING)
                .stream()
                .map(FriendshipDto::from)
                .toList();
    }

    /**
     * Получить исходящие запросы
     */
    @Transactional(readOnly = true)
    public List<FriendshipDto> getOutgoingRequests(Long userId) {
        return friendshipRepository
                .findByRequesterIdAndStatus(userId, FriendshipStatus.PENDING)
                .stream()
                .map(FriendshipDto::from)
                .toList();
    }

    /**
     * Проверить статус связи между пользователями
     */
    @Transactional(readOnly = true)
    public FriendshipStatusDto getFriendshipStatus(Long user1Id, Long user2Id) {
        if (Objects.equals(user1Id, user2Id)) {
            return new FriendshipStatusDto(FriendshipStatus.ACCEPTED, true);
        }

        var friendship = findAnyFriendship(user1Id, user2Id);
        if (friendship == null) {
            return new FriendshipStatusDto(null, false);
        }

        boolean isOutgoing = friendship.getRequester().getId().equals(user1Id);
        return new FriendshipStatusDto(friendship.getStatus(), isOutgoing);
    }

    /**
     * Являются ли пользователи друзьями
     */
    @Cacheable(value = "friendshipCheck", key = "#user1 + '_' + #user2")
    @Transactional(readOnly = true)
    public boolean areFriends(Long user1, Long user2) {
        return friendshipRepository.areFriends(user1, user2);
    }

    /**
     * Получить количество друзей
     */
    @Cacheable(value = "friendsCount", key = "#userId")
    @Transactional(readOnly = true)
    public long getFriendsCount(Long userId) {
        return friendshipRepository.countAcceptedFriends(userId);
    }

    // ==================== PRIVATE HELPERS ====================

    private void validateUsersExist(Long... userIds) {
        for (Long id : userIds) {
            if (!userRepository.existsById(id)) {
                throw new NotFoundException("User not found: " + id);
            }
        }
    }

    private void validateNotSelf(Long requesterId, Long addresseeId) {
        if (Objects.equals(requesterId, addresseeId)) {
            throw new BusinessException("Cannot send friend request to yourself");
        }
    }

    private void validateNoActiveConnection(Long requesterId, Long addresseeId) {
        var existing = friendshipRepository.findByRequesterIdAndAddresseeId(requesterId, addresseeId);
        if (existing.isPresent()) {
            var status = existing.get().getStatus();
            throw switch (status) {
                case PENDING -> new BusinessException("Friend request already pending");
                case ACCEPTED -> new BusinessException("Users are already friends");
                case BLOCKED -> new BusinessException("Cannot send request: user is blocked");
                case REJECTED -> new BusinessException("Friend request was previously rejected");
            };
        }
    }

    private Friendship findAnyFriendship(Long user1, Long user2) {
        return friendshipRepository.findByUserPair(user1, user2).orElse(null);
    }

    private Optional<Friendship> findActiveFriendship(Long user1, Long user2) {
        return friendshipRepository.findByUserPairAndStatusAccepted(user1, user2);
    }

    private void evictFriendCache(Long userId) {
        // Spring Cache автоматически очистит ключи при использовании @CacheEvict
        // Дополнительно можно очистить паттерны, если используете кастомную логику
    }
}