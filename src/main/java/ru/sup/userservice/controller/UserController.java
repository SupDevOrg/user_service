package ru.sup.userservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.webresources.JarWarResourceSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.sup.userservice.dto.*;
import ru.sup.userservice.entity.RefreshToken;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.repository.RefreshTokenRepository;
import ru.sup.userservice.repository.UserRepository;
import ru.sup.userservice.security.JwtUtil;
import ru.sup.userservice.service.UserService;

import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api/v1/user")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Controller", description = "Регистрация, логин и обновление токенов пользователей")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    // ==============================
    //        REGISTER
    // ==============================
    @PostMapping("/register")
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
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            logger.info("Register user with username: {}", request.getUsername());
            AuthResponse response = userService.register(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Username is already in use", e);
            return ResponseEntity.status(409).body("Username is already in use");
        } catch (Exception e) {
            logger.error("Error during user registration", e);
            return ResponseEntity.status(500).body("Registration failed");
        }
    }

    // ==============================
    //        LOGIN
    // ==============================
    @PostMapping("/login")
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
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            logger.info("Login user with username: {}", request.getUsername());
            AuthResponse response = userService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during user login", e);
            return ResponseEntity.status(401).body("Invalid username or password");
        }
    }

    // ==============================
    //        REFRESH TOKEN
    // ==============================
    @PostMapping("/refresh")
    @Operation(
            summary = "Обновление access токена",
            description = "Обновляет access токен по RefreshRequest.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Новый access токен успешно сгенерирован",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "accessToken": "eyJhbGciOiJIUzI1NiIs...",
                                      "refreshToken": null
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован или refresh токен не найден",
                    content = @Content(mediaType = "text/plain",
                            examples = @ExampleObject(value = "Unauthorized"))),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain",
                            examples = @ExampleObject(value = "Internal server error")))
    })


    public AuthResponse refreshByToken(RefreshRequest request) {
        try {
            String refreshTokenValue = request.getRefreshToken();

            if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
                throw new IllegalArgumentException("No refresh token found in request");
            }

            // Находим refresh-токен в БД
            RefreshToken storedToken = refreshTokenRepository.findByToken(refreshTokenValue)
                    .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));

            // Проверяем, не был ли отозван токен
            if (storedToken.isRevoked()) {
                throw new IllegalArgumentException("Refresh token has been revoked");
            }

            // Проверяем срок действия токена
            User user = storedToken.getUser();
            UserDetails userDetails = userService.buildUserDetails(user);

            if (storedToken.getExpiryDate().isAfter(Instant.now())) {
                // Токен ещё жив — создаём новый access token, refresh остаётся прежним
                String newAccessToken = jwtUtil.generateAccessToken(userDetails);
                log.info("Обновлён access-токен по действующему refresh для пользователя {}", user.getUsername());
                return new AuthResponse(newAccessToken, storedToken.getToken());
            } else {
                // Токен истёк — помечаем как revoked
                storedToken.setRevoked(true);
                refreshTokenRepository.save(storedToken);

                // Создаём новый refresh-токен
                RefreshToken newRefreshToken = userService.createAndSaveRefreshToken(user, userDetails);

                // Создаём новый access-токен
                String newAccessToken = jwtUtil.generateAccessToken(userDetails);

                log.info("Refresh-токен обновлён для пользователя {}", user.getUsername());
                return new AuthResponse(newAccessToken, newRefreshToken.getToken());
            }

        } catch (Exception e) {
            log.error("Ошибка при обновлении токена", e);
            throw new RuntimeException("Ошибка обновления токена", e);
        }
    }

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
            @ApiResponse(responseCode = "500", description = "Ошибка при поиске пользователей",
                    content = @Content(mediaType = "text/plain",
                            examples = @ExampleObject(value = "Internal server error")))
    })
    public ResponseEntity<?> getUser(
            @PathVariable String partitionUsername,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<User> usersPage = userRepository.findByUsernameContainingIgnoreCase(partitionUsername, pageable);

            List<UserDto> users = usersPage
                    .getContent()
                    .stream()
                    .map(u -> new UserDto(u.getId(), u.getUsername()))
                    .collect(Collectors.toList());

            if (users.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("users", users);
            response.put("currentPage", usersPage.getNumber());
            response.put("totalItems", usersPage.getTotalElements());
            response.put("totalPages", usersPage.getTotalPages());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error while searching users", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }
}
