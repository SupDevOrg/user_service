package ru.sup.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sup.userservice.entity.RefreshToken;
import ru.sup.userservice.entity.User;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUserAndRevokedFalse(User user);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.user = :user")
    void revokeAllByUser(@Param("user") User user);

}
