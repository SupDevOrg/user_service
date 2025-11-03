package ru.sup.userservice.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.sup.userservice.dto.*;
import ru.sup.userservice.dto.request.LoginRequest;
import ru.sup.userservice.dto.request.RegisterRequest;
import ru.sup.userservice.dto.response.AuthResponse;
import ru.sup.userservice.dto.response.SearchUsersResponse;
import ru.sup.userservice.entity.RefreshToken;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.entity.VerificationCode;
import ru.sup.userservice.kafka.EmailEventProducer;
import ru.sup.userservice.repository.RefreshTokenRepository;
import ru.sup.userservice.repository.UserRepository;
import ru.sup.userservice.repository.VerificationCodeRepository;
import ru.sup.userservice.security.jwt.JwtUtil;
import ru.sup.userservice.util.CodeUtil;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final JwtUtil jwtUtil;
    private final EmailEventProducer emailEventProducer;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshTokenExpirationMs;
    private final String CACHE_NAME = "user-search";

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
            log.info("Trying to authenticate: {}", request.getUsername());
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

    @Transactional
    public AuthResponse update(User user) {
        log.info("Обновление пользователя: {}", user.getUsername());

        // если пароль не закодирован — кодируем
        if (user.getPassword() != null && !user.getPassword().startsWith("$2a$")) {
            log.info("Шифрование нового пароля для {}", user.getUsername());
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        // сохраняем изменения
        userRepository.save(user);
        evictAllSearchCaches();

        // инвалидируем старые refresh токены
        refreshTokenRepository.revokeAllByUser(user);

        // создаём новые токены
        UserDetails userDetails = buildUserDetails(user);
        String accessToken = jwtUtil.generateAccessToken(userDetails);
        RefreshToken refreshToken = createAndSaveRefreshToken(user, userDetails);

        return new AuthResponse(accessToken, refreshToken.getToken());
    }

    public void deleteUser(User user) {
        userRepository.delete(user);
    }

    @Transactional
    public int addEmail(User user, String email) {
        try{
            userRepository.addEmailForUser(user, email);
            String code = CodeUtil.generateCode();

            VerificationCode verificationCode = new VerificationCode();
            verificationCode.setUser_id(user.getId());
            verificationCode.setEmail(email);
            verificationCode.setCode(code);

            verificationCodeRepository.save(verificationCode);
            emailEventProducer.sendEmailCode(email, code);
            return 0;
        } catch (Exception e){
            log.error("Exception while adding email for user: {}, message: {}", user.getUsername(), e.getMessage());
            return 1;
        }
    }

    public int addPhone(User user, String phone) {
        try{
            userRepository.addPhoneForUser(user, phone);
            return 0;
        } catch (Exception e){
            log.error("Exception while adding phone for user: {}, message: {}", user.getUsername(), e.getMessage());
            return 1;
        }
    }

    @Cacheable(
            value = "user-search",
            key = "#prefix + ':' + #page + ':' + #size"
    )
    public SearchUsersResponse searchUsersByUsernamePrefix(String prefix, int page, int size) {
        String trimmed = prefix.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Search prefix cannot be empty");
        }

        Page<User> usersPage = userRepository.findByUsernameStartingWithIgnoreCase(
                trimmed, PageRequest.of(page, size));

        List<UserDto> dtos = usersPage.getContent().stream()
                .map(u -> new UserDto(u.getId(), u.getUsername()))
                .toList();

        log.info("Cache MISS for search: prefix={}, page={}, size={}", trimmed, page, size);

        return new SearchUsersResponse(
                dtos,
                usersPage.getNumber(),
                usersPage.getTotalElements(),
                usersPage.getTotalPages()
        );
    }

    // Опционально: метод для очистки кэша (например, при обновлении пользователя)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void evictAllSearchCaches() {
        log.info("All user search caches evicted");
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

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }



}
