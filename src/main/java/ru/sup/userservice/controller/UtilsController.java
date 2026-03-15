package ru.sup.userservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import ru.sup.userservice.dto.UserDto;
import ru.sup.userservice.dto.request.VerificationEmailRequest;
import ru.sup.userservice.dto.response.SearchUsersResponse;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.service.AvatarStorageService;
import ru.sup.userservice.service.UserService;

import java.util.List;

@RestController
@RequestMapping("api/v1/user")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Utils Controller", description = "Поиск пользователей, подтверждение почты")
public class UtilsController {

    private final UserService userService;
        private final AvatarStorageService avatarStorageService;


    // ==============================
    //        SEARCH USERS
    // ==============================
    @GetMapping("/{partitionUsername}")
    @Operation(
            summary = "Поиск пользователей по подстроке имени с пагинацией",
            description = "Возвращает пользователей, чьи имена содержат указанную подстроку."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пользователи найдены",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "users": [
                                        {"id": 1, "username": "johndoe"},
                                        {"id": 2, "username": "john_smith"}
                                      ],
                                      "currentPage": 0,
                                      "totalItems": 2,
                                      "totalPages": 1
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "Пользователи не найдены"),
            @ApiResponse(responseCode = "403", description = "Пользователь не авторизован"),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос"),
            @ApiResponse(responseCode = "500", description = "Ошибка при поиске пользователей")
    })
    public ResponseEntity<SearchUsersResponse> getUser(
            @PathVariable String partitionUsername,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        try {
            String currentUsername = authentication != null ? authentication.getName() : null;
            SearchUsersResponse response = userService.searchUsersByUsernamePrefix(
                    partitionUsername,
                    page,
                    size,
                    currentUsername
            );

            List<UserDto> usersWithAccessUrls = response.getUsers().stream()
                    .map(this::withPresignedAvatar)
                    .toList();

            response = new SearchUsersResponse(
                    usersWithAccessUrls,
                    response.getCurrentPage(),
                    response.getTotalItems(),
                    response.getTotalPages()
            );

            if (response.getUsers().isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null); // или кастомный error
        } catch (Exception e) {
            log.error("Error while searching users by prefix: {}", partitionUsername, e);
            return ResponseEntity.status(500).body(null);
        }
    }

    // ==============================
    //        GET USER BY ID
    // ==============================
    @GetMapping("/id/{userId}")
    @Operation(
            summary = "Получить пользователя по ID",
            description = "Возвращает username и avatarURL пользователя по его ID.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пользователь найден"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    public ResponseEntity<UserDto> getUserById(@PathVariable Long userId) {
        return userService.getUserById(userId)
                                .map(this::withPresignedAvatar)
                                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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

    // ==============================
    //         VERIFY EMAIL
    // ==============================
    @Operation(
            summary = "Подтверждение Email",
            description = "Подтверждает Email у пользователя.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email подтвержден"),
            @ApiResponse(responseCode = "406", description = "Код неверный"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера"),
    })
    @PostMapping("/verifyEmail")
    public ResponseEntity<?> verifyEmail(
            @RequestBody VerificationEmailRequest request,
            Authentication authentication
    ){
        try{
            String currentUsername = authentication.getName();
            User user = userService.findByUsername(currentUsername)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            int status = userService.verifyEmail(user, request.getCode());
            log.info("Email verification with status: {}", status);
            return switch (status) {
                case 0 -> ResponseEntity.ok().build();
                case 1 -> ResponseEntity.status(406).build();
                default -> ResponseEntity.status(500).build();
            };
        } catch (Exception e){
            return ResponseEntity.status(500).build();
        }
    }
}
