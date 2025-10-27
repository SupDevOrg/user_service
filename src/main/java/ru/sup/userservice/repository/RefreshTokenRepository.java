package ru.sup.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sup.userservice.entity.RefreshToken;
import ru.sup.userservice.entity.User;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteAllByUser(User user);

    void deleteByUser(User user);

    Optional<RefreshToken> findByUser(User user);

    Optional<RefreshToken> findByUserAndRevokedFalse(User user);
}
