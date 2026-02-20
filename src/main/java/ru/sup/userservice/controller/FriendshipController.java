package ru.sup.userservice.controller;


import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;
import ru.sup.userservice.dto.FriendshipDto;
import ru.sup.userservice.dto.FriendshipStatusDto;
import ru.sup.userservice.dto.UserDto;
import ru.sup.userservice.service.FriendshipService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "Friendship Management", description = "API для управления дружбой между пользователями")
@SecurityRequirement(name = "bearerAuth")
public class FriendshipController {

    private final FriendshipService friendshipService;

    /**
     * Отправить запрос в друзья
     */
    @Operation(
            summary = "Отправить запрос в друзья",
            description = "Отправляет запрос на добавление в друзья другому пользователю. " +
                    "Требует аутентификации. ID в пути должен совпадать с ID авторизованного пользователя."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Запрос успешно отправлен",
                    content = @Content(schema = @Schema(implementation = FriendshipDto.class))),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос (сам себе, уже есть запрос)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён (userId не совпадает с авторизованным)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Конфликт (уже друзья или заблокирован)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{userId}/friends/{friendId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<FriendshipDto> sendFriendRequest(
            @Parameter(description = "ID авторизованного пользователя", required = true, example = "1")
            @PathVariable @Min(1) Long userId,

            @Parameter(description = "ID пользователя, которому отправляется запрос", required = true, example = "2")
            @PathVariable @Min(1) Long friendId)
    {

        log.info("Sending friend request: user {} -> {}", userId, friendId);

        var friendship = friendshipService.sendFriendRequest(userId, friendId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", "/api/v1/users/" + userId + "/friends/" + friendId)
                .body(friendship);
    }

    /**
     * Принять запрос в друзья
     */
    @Operation(
            summary = "Принять запрос в друзья",
            description = "Принимает входящий запрос на дружбу. " +
                    "Требует аутентификации. ID в пути должен совпадать с ID получателя запроса."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Запрос принят",
                    content = @Content(schema = @Schema(implementation = FriendshipDto.class))),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Запрос не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{userId}/friends/{friendId}/accept")
    public ResponseEntity<FriendshipDto> acceptFriendRequest(
            @Parameter(description = "ID авторизованного пользователя (получатель запроса)", required = true, example = "2")
            @PathVariable @Min(1) Long userId,

            @Parameter(description = "ID пользователя, который отправил запрос", required = true, example = "1")
            @PathVariable @Min(1) Long friendId)
    {

        log.info("Accepting friend request: user {} <- {}", userId, friendId);

        var friendship = friendshipService.acceptFriendRequest(userId, friendId);
        return ResponseEntity.ok(friendship);
    }

    /**
     * Отклонить запрос в друзья
     */
    @Operation(
            summary = "Отклонить запрос в друзья",
            description = "Отклоняет входящий запрос на дружбу."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Запрос отклонён"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Запрос не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{userId}/friends/{friendId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectFriendRequest(
            @Parameter(description = "ID авторизованного пользователя (получатель запроса)", required = true, example = "2")
            @PathVariable @Min(1) Long userId,

            @Parameter(description = "ID пользователя, который отправил запрос", required = true, example = "1")
            @PathVariable @Min(1) Long friendId)
    {

        log.info("Rejecting friend request: user {} -/-> {}", userId, friendId);

        friendshipService.rejectFriendRequest(userId, friendId);
    }

    /**
     * Отменить исходящий запрос
     */
    @Operation(
            summary = "Отменить исходящий запрос",
            description = "Отменяет отправленный запрос на дружбу."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Запрос отменён"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Запрос не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{userId}/friends/{friendId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelFriendRequest(
            @Parameter(description = "ID авторизованного пользователя (отправитель запроса)", required = true, example = "1")
            @PathVariable @Min(1) Long userId,

            @Parameter(description = "ID пользователя, которому был отправлен запрос", required = true, example = "2")
            @PathVariable @Min(1) Long friendId)
    {

        log.info("Cancelling friend request: user {} -/-> {}", userId, friendId);

        friendshipService.cancelFriendRequest(userId, friendId);
    }

    /**
     * Удалить друга
     */
    @Operation(
            summary = "Удалить друга",
            description = "Разрывает дружескую связь с пользователем."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Друг удалён"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Дружба не найдена",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{userId}/friends/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFriend(
            @Parameter(description = "ID авторизованного пользователя", required = true, example = "1")
            @PathVariable @Min(1) Long userId,

            @Parameter(description = "ID друга, которого нужно удалить", required = true, example = "2")
            @PathVariable @Min(1) Long friendId)
    {

        log.info("Removing friend: user {} -/-> {}", userId, friendId);

        friendshipService.removeFriend(userId, friendId);
    }

    /**
     * Заблокировать пользователя
     */
    @Operation(
            summary = "Заблокировать пользователя",
            description = "Блокирует пользователя. Заблокированный пользователь не может отправлять запросы."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Пользователь заблокирован"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{userId}/friends/{targetId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void blockUser(
            @Parameter(description = "ID авторизованного пользователя", required = true, example = "1")
            @PathVariable @Min(1) Long userId,

            @Parameter(description = "ID пользователя для блокировки", required = true, example = "2")
            @PathVariable @Min(1) Long targetId)
    {

        log.info("Blocking user: user {} -X-> {}", userId, targetId);

        friendshipService.blockUser(userId, targetId);
    }

    /**
     * Разблокировать пользователя
     */
    @Operation(
            summary = "Разблокировать пользователя",
            description = "Снимает блокировку с пользователя."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Пользователь разблокирован"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{userId}/friends/{targetId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblockUser(
            @Parameter(description = "ID авторизованного пользователя", required = true, example = "1")
            @PathVariable @Min(1) Long userId,

            @Parameter(description = "ID пользователя для разблокировки", required = true, example = "2")
            @PathVariable @Min(1) Long targetId)
    {

        log.info("Unblocking user: user {} -/X-> {}", userId, targetId);

        friendshipService.unblockUser(userId, targetId);
    }

    /**
     * Получить список друзей
     */
    @Operation(
            summary = "Получить список друзей",
            description = "Возвращает分页ированный список друзей пользователя."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список друзей получен",
                    content = @Content(schema = @Schema(implementation = UserDto.class))),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{userId}/friends")
    public ResponseEntity<Page<UserDto>> getFriends(
            @Parameter(description = "ID пользователя", required = true, example = "1")
            @PathVariable @Min(1) Long userId,

            @Parameter(description = "Номер страницы (начиная с 0)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) Integer page,

            @Parameter(description = "Размер страницы", example = "20")
            @RequestParam(defaultValue = "20") @Positive Integer size,

            @Parameter(description = "Сортировка (например: username,asc)", example = "username,asc")
            @RequestParam(required = false) String sort) {

        // Опционально: проверить доступ (публичные профили или только свои друзья)
        // if (!currentUser.getId().equals(userId)) { ... }

        Pageable pageable = createPageable(page, size, sort);
        var friends = friendshipService.getFriendsPage(userId, pageable);

        return ResponseEntity.ok(friends);
    }

    /**
     * Получить входящие запросы
     */
    @Operation(
            summary = "Получить входящие запросы",
            description = "Возвращает список всех входящих запросов на дружбу."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Запросы получены",
                    content = @Content(schema = @Schema(implementation = FriendshipDto.class))),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{userId}/friends/requests/incoming")
    public ResponseEntity<List<FriendshipDto>> getIncomingRequests(
            @Parameter(description = "ID авторизованного пользователя", required = true, example = "1")
            @PathVariable @Min(1) Long userId)
    {

        var requests = friendshipService.getIncomingRequests(userId);
        return ResponseEntity.ok(requests);
    }

    /**
     * Получить исходящие запросы
     */
    @Operation(
            summary = "Получить исходящие запросы",
            description = "Возвращает список всех исходящих запросов на дружбу."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Запросы получены",
                    content = @Content(schema = @Schema(implementation = FriendshipDto.class))),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{userId}/friends/requests/outgoing")
    public ResponseEntity<List<FriendshipDto>> getOutgoingRequests(
            @Parameter(description = "ID авторизованного пользователя", required = true, example = "1")
            @PathVariable @Min(1) Long userId)
    {

        var requests = friendshipService.getOutgoingRequests(userId);
        return ResponseEntity.ok(requests);
    }

    /**
     * Проверить статус дружбы
     */
    @Operation(
            summary = "Проверить статус дружбы",
            description = "Возвращает статус связи между двумя пользователями."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Статус получен",
                    content = @Content(schema = @Schema(implementation = FriendshipStatusDto.class))),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{userId}/friends/{friendId}/status")
    public ResponseEntity<FriendshipStatusDto> getFriendshipStatus(
            @Parameter(description = "ID первого пользователя", required = true, example = "1")
            @PathVariable @Min(1) Long userId,

            @Parameter(description = "ID второго пользователя", required = true, example = "2")
            @PathVariable @Min(1) Long friendId) {

        var status = friendshipService.getFriendshipStatus(userId, friendId);
        return ResponseEntity.ok(status);
    }

    /**
     * Получить количество друзей
     */
    @Operation(
            summary = "Получить количество друзей",
            description = "Возвращает общее количество друзей пользователя."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Количество получено"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{userId}/friends/count")
    public ResponseEntity<Long> getFriendsCount(
            @Parameter(description = "ID пользователя", required = true, example = "1")
            @PathVariable @Min(1) Long userId) {

        var count = friendshipService.getFriendsCount(userId);
        return ResponseEntity.ok(count);
    }

    /**
     * Проверить, являются ли пользователи друзьями
     */
    @Operation(
            summary = "Проверить дружбу",
            description = "Возвращает true, если пользователи являются друзьями."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Проверка выполнена")
    })
    @GetMapping("/{userId}/friends/{friendId}/check")
    public ResponseEntity<Boolean> areFriends(
            @Parameter(description = "ID первого пользователя", required = true, example = "1")
            @PathVariable @Min(1) Long userId,

            @Parameter(description = "ID второго пользователя", required = true, example = "2")
            @PathVariable @Min(1) Long friendId) {

        var areFriends = friendshipService.areFriends(userId, friendId);
        return ResponseEntity.ok(areFriends);
    }

    // ==================== PRIVATE HELPERS ====================

    private Pageable createPageable(Integer page, Integer size, String sort) {
        if (sort != null && !sort.isEmpty()) {
            String[] sortParams = sort.split(",");
            if (sortParams.length == 2) {
                org.springframework.data.domain.Sort.Direction direction =
                        sortParams[1].equalsIgnoreCase("desc")
                                ? org.springframework.data.domain.Sort.Direction.DESC
                                : org.springframework.data.domain.Sort.Direction.ASC;
                return org.springframework.data.domain.PageRequest.of(page, size,
                        org.springframework.data.domain.Sort.by(direction, sortParams[0]));
            }
        }
        return org.springframework.data.domain.PageRequest.of(page, size);
    }
}