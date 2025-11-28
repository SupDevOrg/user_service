package ru.sup.userservice.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import ru.sup.userservice.repository.UserRepository;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
@Slf4j
public class JwtUtil {

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;
    private final UserRepository userRepository;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-ms}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshTokenExpirationMs,
            UserRepository userRepository
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.userRepository = userRepository;
    }

    /** Генерация Access-токена */
    public String generateAccessToken(UserDetails userDetails) {
        if (userRepository.findByUsername(userDetails.getUsername()).isPresent()){
            return buildToken(userDetails.getUsername(), userRepository.findByUsername(userDetails.getUsername()).get().getId(),  accessTokenExpirationMs);
        } else return null;

    }

    /** Генерация Refresh-токена */
    public String generateRefreshToken(UserDetails userDetails) {
        if (userRepository.findByUsername(userDetails.getUsername()).isPresent()) {
            return buildToken(userDetails.getUsername(), userRepository.findByUsername(userDetails.getUsername()).get().getId(), refreshTokenExpirationMs);
        } else return null;
    }

    /** Вспомогательный метод */
    private String buildToken(String username, Long id, long expiration) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", id)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Получение имени пользователя */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /** Проверка токена */
    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Проверка срока действия */
    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    /** Разбор JWT и получение всех Claims */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
