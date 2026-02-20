package ru.sup.userservice.repository;

import lombok.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.sup.userservice.dto.UserDto;
import ru.sup.userservice.entity.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String name);

    Page<User> findByUsernameStartingWithIgnoreCase(String username, Pageable pageable);

    /**
     * Проверка существования пользователя по ID
     */
    boolean existsById(@NonNull Long id);

    /**
     * Получить список пользователей по ID (для друзей)
     */
    @Query("""
        SELECT new ru.sup.userservice.dto.UserDto(u.id, u.username, u.avatarURL)
        FROM User u 
        WHERE u.id IN :ids
        """)
    List<UserDto> findUserDtoByIds(@Param("ids") List<Long> ids);

    /**
     * Получить список пользователей по ID с пагинацией
     */
    @Query("""
        SELECT new ru.sup.userservice.dto.UserDto(u.id, u.username, u.avatarURL)
        FROM User u 
        WHERE u.id IN :ids
        """)
    Page<UserDto> findUserDtoByIds(@Param("ids") List<Long> ids, Pageable pageable);

    /**
     * Получить DTO пользователя по ID
     */
    @Query("""
        SELECT new ru.sup.userservice.dto.UserDto(u.id, u.username, u.avatarURL)
        FROM User u 
        WHERE u.id = :id
        """)
    Optional<UserDto> findUserDtoById(@Param("id") Long id);

    /**
     * Поиск пользователей по началу username (для поиска друзей)
     */
    @Query("""
        SELECT new ru.sup.userservice.dto.UserDto(u.id, u.username, u.avatarURL)
        FROM User u 
        WHERE u.username LIKE CONCAT(:prefix, '%')
        """)
    List<UserDto> findUserDtoByUsernameStartingWith(@Param("prefix") String prefix);
}