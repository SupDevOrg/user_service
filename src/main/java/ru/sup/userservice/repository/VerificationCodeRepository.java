package ru.sup.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.sup.userservice.entity.VerificationCode;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

    @Query("SELECT vc FROM VerificationCode vc WHERE vc.user.id = :userId AND vc.revoked = false")
    Optional<VerificationCode> findActiveByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE VerificationCode vc SET vc.revoked = true WHERE vc.user.id = :userId AND vc.revoked = false")
    void revokeAllVerificationCodes(@Param("userId") Long userId);
}