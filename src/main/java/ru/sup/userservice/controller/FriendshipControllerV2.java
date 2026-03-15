package ru.sup.userservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;
import ru.sup.userservice.dto.FriendshipDto;
import ru.sup.userservice.dto.FriendshipStatusDto;
import ru.sup.userservice.dto.UserDto;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.service.AvatarStorageService;
import ru.sup.userservice.service.FriendshipService;
import ru.sup.userservice.service.UserService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v2/user")
@RequiredArgsConstructor
@Validated
@Tag(name = "Friendship Management V2", description = "API для управления дружбой (userId берётся из JWT токена)")
@SecurityRequirement(name = "bearerAuth")
public class FriendshipControllerV2 {

    private final FriendshipService friendshipService;
        private final UserService userService;
        private final AvatarStorageService avatarStorageService;

    /**
     * Отправить запрос в друзья
     */
    @Operation(summary = "Отправить запрос в друзья")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Запрос успешно отправлен",
                    content = @Content(schema = @Schema(implementation = FriendshipDto.class))),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Конфликт (уже друзья или заблокирован)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/friends/{friendId}")
    public ResponseEntity<FriendshipDto> sendFriendRequest(
            @Parameter(description = "ID пользователя, которому отправляется запрос", required = true, example = "2")
            @PathVariable @Min(1) Long friendId) {

        Long userId = getCurrentUserId();
        log.info("Sending friend request: user {} -> {}", userId, friendId);

        var friendship = friendshipService.sendFriendRequest(userId, friendId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", "/api/v2/user/friends/" + friendId)
                .body(friendship);
    }

    /**
     * Принять запрос в друзья
     */
    @Operation(summary = "Принять запрос в друзья")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Запрос принят",
                    content = @Content(schema = @Schema(implementation = FriendshipDto.class))),
            @ApiResponse(responseCode = "404", description = "Запрос не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/friends/{friendId}/accept")
    public ResponseEntity<FriendshipDto> acceptFriendRequest(
            @Parameter(description = "ID пользователя, который отправил запрос", required = true, example = "1")
            @PathVariable @Min(1) Long friendId) {

        Long userId = getCurrentUserId();
        log.info("Accepting friend request: user {} <- {}", userId, friendId);

        var friendship = friendshipService.acceptFriendRequest(userId, friendId);
        return ResponseEntity.ok(friendship);
    }

    /**
     * Отклонить запрос в друзья
     */
    @Operation(summary = "Отклонить запрос в друзья")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Запрос отклонён"),
            @ApiResponse(responseCode = "404", description = "Запрос не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/friends/{friendId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectFriendRequest(
            @Parameter(description = "ID пользователя, который отправил запрос", required = true, example = "1")
            @PathVariable @Min(1) Long friendId) {

        Long userId = getCurrentUserId();
        log.info("Rejecting friend request: user {} -/-> {}", userId, friendId);

        friendshipService.rejectFriendRequest(userId, friendId);
    }

    /**
     * Отменить исходящий запрос
     */
    @Operation(summary = "Отменить исходящий запрос")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Запрос отменён"),
            @ApiResponse(responseCode = "404", description = "Запрос не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/friends/{friendId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelFriendRequest(
            @Parameter(description = "ID пользователя, которому был отправлен запрос", required = true, example = "2")
            @PathVariable @Min(1) Long friendId) {

        Long userId = getCurrentUserId();
        log.info("Cancelling friend request: user {} -/-> {}", userId, friendId);

        friendshipService.cancelFriendRequest(userId, friendId);
    }

    /**
     * Удалить друга
     */
    @Operation(summary = "Удалить друга")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Друг удалён"),
            @ApiResponse(responseCode = "404", description = "Дружба не найдена",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/friends/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFriend(
            @Parameter(description = "ID друга, которого нужно удалить", required = true, example = "2")
            @PathVariable @Min(1) Long friendId) {

        Long userId = getCurrentUserId();
        log.info("Removing friend: user {} -/-> {}", userId, friendId);

        friendshipService.removeFriend(userId, friendId);
    }

    /**
     * Заблокировать пользователя
     */
    @Operation(summary = "Заблокировать пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Пользователь заблокирован"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/friends/{targetId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void blockUser(
            @Parameter(description = "ID пользователя для блокировки", required = true, example = "2")
            @PathVariable @Min(1) Long targetId) {

        Long userId = getCurrentUserId();
        log.info("Blocking user: user {} -X-> {}", userId, targetId);

        friendshipService.blockUser(userId, targetId);
    }

    /**
     * Разблокировать пользователя
     */
    @Operation(summary = "Разблокировать пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Пользователь разблокирован")
    })
    @DeleteMapping("/friends/{targetId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblockUser(
            @Parameter(description = "ID пользователя для разблокировки", required = true, example = "2")
            @PathVariable @Min(1) Long targetId) {

        Long userId = getCurrentUserId();
        log.info("Unblocking user: user {} -/X-> {}", userId, targetId);

        friendshipService.unblockUser(userId, targetId);
    }

    /**
     * Получить список друзей
     */
    @Operation(summary = "Получить список друзей")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список друзей получен",
                    content = @Content(schema = @Schema(implementation = UserDto.class)))
    })
    @GetMapping("/friends")
    public ResponseEntity<Page<UserDto>> getFriends(
            @Parameter(description = "Номер страницы (начиная с 0)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) Integer page,

            @Parameter(description = "Размер страницы", example = "20")
            @RequestParam(defaultValue = "20") @Positive Integer size,

            @Parameter(description = "Сортировка (например: username,asc)", example = "username,asc")
            @RequestParam(required = false) String sort) {

        Long userId = getCurrentUserId();
        Pageable pageable = createPageable(page, size, sort);
        var friends = friendshipService.getFriendsPage(userId, pageable);
        Page<UserDto> response = friends.map(this::withPresignedAvatar);
        return ResponseEntity.ok(response);
    }

    /**
     * Получить входящие запросы
     */
    @Operation(summary = "Получить входящие запросы на дружбу")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Запросы получены",
                    content = @Content(schema = @Schema(implementation = FriendshipDto.class)))
    })
    @GetMapping("/friends/requests/incoming")
    public ResponseEntity<List<FriendshipDto>> getIncomingRequests() {
        Long userId = getCurrentUserId();
        var requests = friendshipService.getIncomingRequests(userId);
        return ResponseEntity.ok(requests);
    }

    /**
     * Получить исходящие запросы
     */
    @Operation(summary = "Получить исходящие запросы на дружбу")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Запросы получены",
                    content = @Content(schema = @Schema(implementation = FriendshipDto.class)))
    })
    @GetMapping("/friends/requests/outgoing")
    public ResponseEntity<List<FriendshipDto>> getOutgoingRequests() {
        Long userId = getCurrentUserId();
        var requests = friendshipService.getOutgoingRequests(userId);
        return ResponseEntity.ok(requests);
    }

    /**
     * Проверить статус дружбы
     */
    @Operation(summary = "Проверить статус дружбы с пользователем")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Статус получен",
                    content = @Content(schema = @Schema(implementation = FriendshipStatusDto.class)))
    })
    @GetMapping("/friends/{friendId}/status")
    public ResponseEntity<FriendshipStatusDto> getFriendshipStatus(
            @Parameter(description = "ID второго пользователя", required = true, example = "2")
            @PathVariable @Min(1) Long friendId) {

        Long userId = getCurrentUserId();
        var status = friendshipService.getFriendshipStatus(userId, friendId);
        return ResponseEntity.ok(status);
    }

    /**
     * Получить количество друзей
     */
    @Operation(summary = "Получить количество своих друзей")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Количество получено")
    })
    @GetMapping("/friends/count")
    public ResponseEntity<Long> getFriendsCount() {
        Long userId = getCurrentUserId();
        var count = friendshipService.getFriendsCount(userId);
        return ResponseEntity.ok(count);
    }

    /**
     * Проверить, являются ли пользователи друзьями
     */
    @Operation(summary = "Проверить дружбу с пользователем")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Проверка выполнена")
    })
    @GetMapping("/friends/{friendId}/check")
    public ResponseEntity<Boolean> areFriends(
            @Parameter(description = "ID второго пользователя", required = true, example = "2")
            @PathVariable @Min(1) Long friendId) {

        Long userId = getCurrentUserId();
        var areFriends = friendshipService.areFriends(userId, friendId);
        return ResponseEntity.ok(areFriends);
    }

    // ==================== PRIVATE HELPERS ====================

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equalsIgnoreCase(authentication.getName())) {
            throw new UsernameNotFoundException("User is not authenticated");
        }

        return userService.findByUsername(authentication.getName())
                .map(User::getId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private Pageable createPageable(Integer page, Integer size, String sort) {
        if (sort != null && !sort.isEmpty()) {
            String[] sortParams = sort.split(",");
            if (sortParams.length == 2) {
                Sort.Direction direction = sortParams[1].equalsIgnoreCase("desc")
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC;
                return PageRequest.of(page, size, Sort.by(direction, sortParams[0]));
            }
        }
        return PageRequest.of(page, size);
    }

    private UserDto withPresignedAvatar(UserDto userDto) {
        String avatarUrl = userDto.getAvatarURL();
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return userDto;
        }

        return new UserDto(
                userDto.getId(),
                userDto.getUsername(),
                avatarStorageService.createAvatarAccessUrl(avatarUrl)
        );
    }
}
