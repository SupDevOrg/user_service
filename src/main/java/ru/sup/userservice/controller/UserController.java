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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import ru.sup.userservice.dto.request.*;
import ru.sup.userservice.dto.response.AuthResponse;
import ru.sup.userservice.dto.response.SearchUsersResponse;
import ru.sup.userservice.entity.RefreshToken;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.kafka.UserEventProducer;
import ru.sup.userservice.repository.RefreshTokenRepository;
import ru.sup.userservice.security.jwt.JwtUtil;
import ru.sup.userservice.service.UserService;

import java.time.Instant;
import java.util.Optional;

@RestController
@RequestMapping("api/v1/user")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Controller", description = "Регистрация, логин и обновление токенов пользователей")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final UserEventProducer userEventProducer;

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
            logger.info("Register user with username: {}", request.getUsername());
            AuthResponse response = userService.register(request);
            Optional<User> user = userService.findByUsername(request.getUsername());
            if(user.isPresent()){
                userEventProducer.sendUserCreated(user.get().getId(), user.get().getUsername());
                return ResponseEntity.ok(response);
            }else {
                logger.error("Error during user registration");
                return ResponseEntity.status(500).body("Registration failed");
            }
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
            logger.info("Login user with username: {}", request.getUsername());
            AuthResponse response = userService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during user login", e);
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

            if (user != null){
                log.info("Deleting user {}", user.getUsername());
                userService.deleteUser(user);
                return ResponseEntity.ok().build();
            }
        } catch (UsernameNotFoundException e){
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.status(500).build();

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

        boolean changed = false;
        boolean changedUserName = false;
        String oldUserName = user.getUsername();

        // ✅ обновляем логин, если передан
        if (request.getUsername() != null && !request.getUsername().isBlank()
                && !request.getUsername().equals(user.getUsername())) {
            user.setUsername(request.getUsername());
            changed = true;
            changedUserName = true;
        }

        // ✅ обновляем пароль, если передан
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(request.getPassword());
            changed = true;
        }

        if (!changed) {
            return ResponseEntity.badRequest()
                    .body(new AuthResponse("No changes", null));
        }

        // ⚙️ обновляем пользователя через сервис
        AuthResponse response = userService.update(user);
        if (changedUserName){
            userEventProducer.sendUserUpdated(user.getId(),"username", oldUserName, user.getUsername());
        }
        return ResponseEntity.ok(response);
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
            @ApiResponse(responseCode = "400", description = "Некорректный запрос"),
            @ApiResponse(responseCode = "500", description = "Ошибка при поиске пользователей")
    })
    public ResponseEntity<SearchUsersResponse> getUser(
            @PathVariable String partitionUsername,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            SearchUsersResponse response = userService.searchUsersByUsernamePrefix(partitionUsername, page, size);

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
    //           ADD EMAIL
    // ==============================
    @Operation(
            summary = "Добавление Email пользователю",
            description = "Первичное добавление Email пользователю, во время регистрации, в иных случаях следует использовать /update",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email добавлен"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера"),
    })
    @PostMapping("/addEmail")
    public ResponseEntity<?> addEmail(@RequestBody AddEmailRequest request,
                                       Authentication authentication){
        try {
            String currentUsername = authentication.getName();
            User user = userService.findByUsername(currentUsername)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            int status = userService.addEmail(user, request.getEmail());
            switch (status){
                case 0:
                    return ResponseEntity.ok().build();
                case 1:
                    return ResponseEntity.status(500).build();
            }
        } catch (UsernameNotFoundException e){
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.status(500).build();
    }

    // ==============================
    //        ADD PHONE NUMBER
    // ==============================
    @Operation(
            summary = "Добавление номера телефона пользователю",
            description = "Первичное добавление номера телефона пользователю, для обновления следует использовать /update",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Номер телефона добавлен"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера"),
    })
    @PostMapping("/addPhone")
    public ResponseEntity<?> addPhone(@RequestBody AddPhoneRequest request,
                                       Authentication authentication){
        try {
            String currentUsername = authentication.getName();
            User user = userService.findByUsername(currentUsername)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            int status = userService.addPhone(user, request.getPhone());
            switch (status){
                case 0:
                    return ResponseEntity.ok().build();
                case 1:
                    return ResponseEntity.status(500).build();
            }
        } catch (UsernameNotFoundException e){
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.status(500).build();
    }

    // ==============================
    //        REFRESH TOKEN
    // ==============================
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
    @PostMapping("/refresh")
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

}
