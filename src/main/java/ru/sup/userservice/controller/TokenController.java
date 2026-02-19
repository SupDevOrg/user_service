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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sup.userservice.dto.request.RefreshRequest;
import ru.sup.userservice.dto.response.AuthResponse;
import ru.sup.userservice.entity.RefreshToken;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.repository.RefreshTokenRepository;
import ru.sup.userservice.security.jwt.JwtUtil;
import ru.sup.userservice.service.UserService;

import java.time.Instant;

@RestController
@RequestMapping("api/v1/user")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Token Controller", description = "Обновление access токена")
public class TokenController {

    private static final Logger logger = LoggerFactory.getLogger(TokenController.class);

    private final UserService userService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

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
