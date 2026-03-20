package ru.sup.userservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import ru.sup.userservice.dto.request.*;
import ru.sup.userservice.dto.response.AvatarAccessUrlResponse;
import ru.sup.userservice.dto.response.AuthResponse;
import ru.sup.userservice.dto.response.AvatarUploadUrlResponse;
import ru.sup.userservice.dto.response.UserProfileResponse;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.kafka.UserEventProducer;
import ru.sup.userservice.service.AvatarStorageService;
import ru.sup.userservice.service.UserService;

import java.util.Optional;

@RestController
@RequestMapping("api/v1/user")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Controller", description = "Регистрация, логин и обновление данных пользователей")
public class UserController {


    private final UserService userService;
    private final UserEventProducer userEventProducer;
    private final AvatarStorageService avatarStorageService;

    // ==============================
    //        REGISTER
    // ==============================
    @Operation(
            summary = "Регистрация нового пользователя",
            description = "Создаёт нового пользователя и возвращает access и refresh токены."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пользователь успешно зарегистрирован",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "accessToken": "eyJhbGciOiJIUzI1NiIs...",
                                      "refreshToken": "null"
                                    }
                                    """))),
            @ApiResponse(responseCode = "409", description = "Имя пользователя уже используется",
                    content = @Content(mediaType = "text/plain",
                            examples = @ExampleObject(value = "Username is already in use"))),
            @ApiResponse(responseCode = "500", description = "Ошибка при регистрации",
                    content = @Content(mediaType = "text/plain",
                            examples = @ExampleObject(value = "Registration failed")))
    })
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            log.info("Register user with username: {}", request.getUsername());
            AuthResponse response = userService.register(request);
            Optional<User> user = userService.findByUsername(request.getUsername());
            if(user.isPresent()){
                userEventProducer.sendUserCreated(user.get().getId(), user.get().getUsername());
                return ResponseEntity.ok(response);
            }else {
                log.error("Error during user registration");
                return ResponseEntity.status(500).body("Registration failed");
            }
        } catch (IllegalArgumentException e) {
            log.error("Username is already in use", e);
            return ResponseEntity.status(409).body("Username is already in use");
        } catch (Exception e) {
            log.error("Error during user registration", e);
            return ResponseEntity.status(500).body("Registration failed");
        }
    }

    // ==============================
    //        LOGIN
    // ==============================
    @Operation(
            summary = "Логин пользователя",
            description = "Авторизует пользователя и возвращает пару токенов."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешный вход",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "accessToken": "eyJhbGciOiJIUzI1NiIs...",
                                      "refreshToken": "null"
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Неверное имя пользователя или пароль",
                    content = @Content(mediaType = "text/plain",
                            examples = @ExampleObject(value = "Invalid username or password"))),
            @ApiResponse(responseCode = "500", description = "Ошибка при авторизации",
                    content = @Content(mediaType = "text/plain",
                            examples = @ExampleObject(value = "Internal server error")))
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            log.info("Login user with username: {}", request.getUsername());
            AuthResponse response = userService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during user login", e);
            return ResponseEntity.status(401).body("Invalid username or password");
        }
    }

    // ==============================
    //        DELETE USER
    // ==============================
    @Operation(
            summary = "Удаление пользователя",
            description = "Удаляет пользователя, который отправил запрос",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пользователь удалён"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера"),
    })
    @DeleteMapping("/delete")
    public ResponseEntity<?> delete(Authentication authentication){
        try {
            String currentUsername = authentication.getName();
            User user = userService.findByUsername(currentUsername)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));
            log.info("Deleting user {}", user.getUsername());
            userService.deleteUser(user);
            return ResponseEntity.ok().build();
        } catch (UsernameNotFoundException e){
            return ResponseEntity.status(401).build();
        }
    }

    // ==============================
    //        UPDATE USER
    // ==============================
    @Operation(
            summary = "Обновление данных пользователя",
            description = "Позволяет изменить логин и/или пароль текущего пользователя",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Данные пользователя успешно обновлены"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    })
    @PutMapping("/update")
    public ResponseEntity<AuthResponse> update(@RequestBody UpdateRequest request,
                                               Authentication authentication) {
        String currentUsername = authentication.getName();
        User user = userService.findByUsername(currentUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        User newData = new User();
        boolean changed = false;
        boolean changedUserName = false;
        String oldUserName = user.getUsername();

        // ✅ обновляем логин, если передан
        if (request.getUsername() != null && !request.getUsername().isBlank()
                && !request.getUsername().equals(user.getUsername())) {
            newData.setUsername(request.getUsername());
            changed = true;
            changedUserName = true;
        }

        // ✅ обновляем пароль, если передан
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            newData.setPassword(request.getPassword());
            changed = true;
        }

        // ✅ обновляем email, если передан
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            newData.setEmail(request.getEmail());
            changed = true;
        }

        // ✅ обновляем phone, если передан
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            newData.setPhone(request.getPhone());
            changed = true;
        }

        if (!changed) {
            return ResponseEntity.badRequest()
                    .body(new AuthResponse("No changes", null));
        }

        // ⚙️ обновляем пользователя через сервис
        AuthResponse response = userService.update(user, newData);
        if (changedUserName){
            userEventProducer.sendUserUpdated(user.getId(),"username", oldUserName, user.getUsername());
        }
        return ResponseEntity.ok(response);
    }

        @Operation(
            summary = "Создание URL для загрузки аватарки",
            description = "Возвращает presigned URL для загрузки в бакет и сохраняет публичный avatarURL в профиле",
            security = @SecurityRequirement(name = "bearerAuth")
        )
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "URL для загрузки успешно создан"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
        })
        @PostMapping("/avatar/upload-url")
        public ResponseEntity<AvatarUploadUrlResponse> createAvatarUploadUrl(
            @RequestBody AvatarUploadUrlRequest request,
            Authentication authentication
        ) {
        String currentUsername = authentication.getName();
        User user = userService.findByUsername(currentUsername)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String oldAvatar = user.getAvatarURL();
        AvatarUploadUrlResponse response = avatarStorageService.createAvatarUploadUrl(
            user.getId(),
            request.getContentType(),
            request.getFileName()
        );

        userService.updateAvatarUrl(user, response.getAvatarUrl());
        userEventProducer.sendUserUpdated(user.getId(), "avatarURL", oldAvatar, response.getAvatarUrl());

        return ResponseEntity.ok(response);
        }

        @Operation(
            summary = "Создание URL для чтения аватарки",
            description = "Возвращает краткоживущий presigned GET URL для приватной аватарки текущего пользователя",
            security = @SecurityRequirement(name = "bearerAuth")
        )
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "URL для чтения успешно создан"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
            @ApiResponse(responseCode = "404", description = "У пользователя нет аватарки")
        })
        @GetMapping("/avatar/access-url")
        public ResponseEntity<AvatarAccessUrlResponse> createAvatarAccessUrl(Authentication authentication) {
        String currentUsername = authentication.getName();
        User user = userService.findByUsername(currentUsername)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String avatarUrl = user.getAvatarURL();
        if (avatarUrl == null || avatarUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Avatar not found");
        }

        String accessUrl = avatarStorageService.createAvatarAccessUrl(avatarUrl);
        return ResponseEntity.ok(new AvatarAccessUrlResponse(accessUrl, avatarStorageService.getDownloadUrlExpirySeconds()));
        }

    // ==============================
    //        GET MY PROFILE
    // ==============================
    @Operation(
            summary = "Получить профиль текущего пользователя",
            description = "Возвращает всю информацию о пользователе, отправившем запрос.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Профиль успешно получен",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(Authentication authentication) {
        String currentUsername = authentication.getName();
        User user = userService.findByUsername(currentUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String avatarUrl = null;
        if (user.getAvatarURL() != null && !user.getAvatarURL().isBlank()) {
            avatarUrl = avatarStorageService.createAvatarAccessUrl(user.getAvatarURL());
        }

        UserProfileResponse profile = new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.isEmailVerification(),
                avatarUrl
        );

        return ResponseEntity.ok(profile);
    }

}
