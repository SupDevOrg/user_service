package ru.sup.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.sup.userservice.entity.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String name);
    org.springframework.data.domain.Page<User> findByUsernameContainingIgnoreCase(String username, org.springframework.data.domain.Pageable pageable);

}
