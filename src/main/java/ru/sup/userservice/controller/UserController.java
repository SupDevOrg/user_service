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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import ru.sup.userservice.dto.request.*;
import ru.sup.userservice.dto.response.AuthResponse;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.kafka.UserEventProducer;
import ru.sup.userservice.service.UserService;

import java.util.Optional;

@RestController
@RequestMapping("api/v1/user")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Controller", description = "Регистрация, логин и обновление данных пользователей")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
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





}
