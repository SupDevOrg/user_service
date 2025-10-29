package ru.sup.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.sup.userservice.dto.AuthResponse;
import ru.sup.userservice.dto.LoginRequest;
import ru.sup.userservice.dto.RefreshRequest;
import ru.sup.userservice.dto.RegisterRequest;
import ru.sup.userservice.entity.RefreshToken;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.repository.RefreshTokenRepository;
import ru.sup.userservice.repository.UserRepository;
import ru.sup.userservice.security.JwtUtil;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshTokenExpirationMs;

    /** Регистрация нового пользователя */
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            log.error("Пользователь с именем '{}' уже существует", request.getUsername());
            throw new IllegalArgumentException("Пользователь уже существует");
        }

        try {
            // Создаём нового пользователя
            User user = new User();
            user.setUsername(request.getUsername());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            userRepository.save(user);

            // Создаём UserDetails (для JWT)
            UserDetails userDetails = buildUserDetails(user);

            // Генерируем токены
            String accessToken = jwtUtil.generateAccessToken(userDetails);
            String refreshToken = jwtUtil.generateRefreshToken(userDetails);

            // Сохраняем refresh-токен в БД
            RefreshToken refreshTokenObj = RefreshToken.builder()
                    .token(refreshToken)
                    .user(user)
                    .expiryDate(Instant.now().plusMillis(refreshTokenExpirationMs))
                    .revoked(false)
                    .build();

            refreshTokenRepository.save(refreshTokenObj);

            // Возвращаем только access-токен (refresh не отправляем клиенту)
            return new AuthResponse(accessToken, refreshToken);

        } catch (Exception e) {
            log.error("Ошибка при регистрации пользователя", e);
            throw new RuntimeException("Ошибка регистрации", e);
        }
    }

    /** Логин пользователя */
    public AuthResponse login(LoginRequest request) {
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            UserDetails userDetails = buildUserDetails(user);

            String accessToken = jwtUtil.generateAccessToken(userDetails);

            // Проверяем, есть ли актуальный refresh-токен
            RefreshToken refreshToken = refreshTokenRepository
                    .findByUserAndRevokedFalse(user)
                    .orElseGet(() -> createAndSaveRefreshToken(user, userDetails));

            // Если токен просрочен — создаём новый
            if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
                refreshToken.setRevoked(true);
                refreshTokenRepository.save(refreshToken);
                refreshToken = createAndSaveRefreshToken(user, userDetails);
            }

            return new AuthResponse(accessToken, refreshToken.getToken());

        } catch (Exception e) {
            log.error("Ошибка при логине пользователя", e);
            throw new RuntimeException("Ошибка логина", e);
        }
    }

    /** Обновление access-токена по существующему refresh-токену */
    public String refreshByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        RefreshToken refreshToken = refreshTokenRepository.findByUserAndRevokedFalse(user)
                .orElse(null);

        if (refreshToken == null || refreshToken.getExpiryDate().isBefore(Instant.now())) {
            log.warn("Refresh token отсутствует или истёк для пользователя {}", username);
            return null;
        }

        UserDetails userDetails = buildUserDetails(user);
        return jwtUtil.generateAccessToken(userDetails);
    }

    /** Вспомогательный метод: создать и сохранить новый refresh-токен */
    public RefreshToken createAndSaveRefreshToken(User user, UserDetails userDetails) {
        String tokenValue = jwtUtil.generateRefreshToken(userDetails);
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenValue)
                .user(user)
                .expiryDate(Instant.now().plusMillis(refreshTokenExpirationMs))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    /** Построение UserDetails из User */
    public UserDetails buildUserDetails(User user) {
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities("USER")
                .build();
    }
}
