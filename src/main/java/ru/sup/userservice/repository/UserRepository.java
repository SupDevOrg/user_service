package ru.sup.userservice.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.sup.userservice.entity.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String name);

    Page<User> findByUsernameStartingWithIgnoreCase(String username, Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.email = :email WHERE u = :user")
    void addEmailForUser(@Param("user") User user, @Param("email") String email);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.phone = :phone WHERE u = :user")
    void addPhoneForUser(@Param("user") User user, @Param("phone") String phone);
}
