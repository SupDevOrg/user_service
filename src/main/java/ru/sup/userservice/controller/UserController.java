package ru.sup.userservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.websocket.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.sup.userservice.dto.*;
import ru.sup.userservice.repository.UserRepository;
import ru.sup.userservice.service.UserService;

import java.util.List;
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

    @PostMapping("/register")
    @Operation(summary = "Регистрация нового пользователя")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            logger.info("Register user with username: {}", request.getUsername());
            AuthResponse response = userService.register(request);

            if (response != null) {
                return ResponseEntity.ok(response);
            } else {
                throw new AuthenticationException("Registration failed: invalid data");
            }
        }
        catch (IllegalArgumentException e){
            logger.error("Username is already in use", e);
            return ResponseEntity.status(409).body("Username is already in use");
        }
        catch (Exception e) {
            logger.error("Error during user registration", e);
            return ResponseEntity.status(500).body("Registration failed");
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Логин пользователя")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            logger.info("Login user with username: {}", request.getUsername());
            AuthResponse response = userService.login(request);

            if (response != null) {
                return ResponseEntity.ok(response);
            } else {
                throw new AuthenticationException("Login failed: invalid credentials");
            }
        } catch (Exception e) {
            logger.error("Error during user login", e);
            return ResponseEntity.status(401).body("Invalid username or password");
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "Обновление токена")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        try {
            AuthResponse response = userService.refresh(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during token refresh", e);
            return ResponseEntity.status(401).body("Invalid refresh token");
        }
    }

    @GetMapping("/{partitionUsername}")
    public ResponseEntity<?> getUser(@PathVariable String partitionUsername) {
        List<UserDto> users = userRepository.findByUsernameContainingIgnoreCase(partitionUsername)
                .stream()
                .map(u -> new UserDto(u.getId(), u.getUsername()))
                .collect(Collectors.toList());

        if (users.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(users);
    }
}
