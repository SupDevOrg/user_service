package ru.sup.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import ru.sup.userservice.entity.User;
import ru.sup.userservice.repository.UserRepository;
import ru.sup.userservice.security.JwtUtil;


//TODO make this
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public AuthResponse register(RegisterRequest request) {
        try {
            // Проверяем, нет ли уже пользователя с таким логином
            if (userRepository.findByUsername(request.getUsername()).isPresent()) {
                log.error("Пользователь с именем '{}' уже существует", request.getUsername());
                throw new IllegalArgumentException("Пользователь уже существует");
            }

            // Создаём нового пользователя
            User user = new User();
            user.setUsername(request.getUsername());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            userRepository.save(user);

            // Создаём UserDetails (для совместимости с JwtUtil)
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername(user.getUsername())
                    .password(user.getPassword())
                    .authorities("USER") // пока что одна базовая роль
                    .build();

            // Генерируем токены
            String accessToken = jwtUtil.generateAccessToken(userDetails);
            String refreshToken = jwtUtil.generateRefreshToken(userDetails);

            // Возвращаем ответ
            AuthResponse response = new AuthResponse();
            response.setAccessToken(accessToken);
            response.setRefreshToken(refreshToken);
            return response;

        } catch (Exception e) {
            log.error("Ошибка при регистрации пользователя", e);
            throw new RuntimeException("Ошибка регистрации", e);
        }
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            UserDetails user = userDetailsService.loadUserByUsername(request.getUsername());

            String accessToken = jwtUtil.generateAccessToken(user);
            String refreshToken = jwtUtil.generateRefreshToken(user);

            AuthResponse response = new AuthResponse();
            response.setAccessToken(accessToken);
            response.setRefreshToken(refreshToken);

            return response;

        } catch (Exception e) {
            logger.error("Error during login: ", e);
            return null;
        }
    }

    public AuthResponse refresh(RefreshRequest request) {
        try {
            String refreshToken = request.getRefreshToken();
            String username = jwtUtil.extractUsername(refreshToken);

            UserDetails user = userDetailsService.loadUserByUsername(username);

            if (jwtUtil.validateToken(refreshToken, user)) {
                String newAccessToken = jwtUtil.generateAccessToken(user);
                String newRefreshToken = jwtUtil.generateRefreshToken(user);

                AuthResponse response = new AuthResponse();
                response.setAccessToken(newAccessToken);
                response.setRefreshToken(newRefreshToken);

                return response;
            } else {
                throw new Exception("Invalid refresh token");
            }

        } catch (Exception e) {
            logger.error("Error during token refresh: ", e);
            return null;
        }
    }
}
