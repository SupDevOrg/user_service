package ru.sup.userservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.sup.userservice.dto.UserDto;
import ru.sup.userservice.entity.User;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(LiquibaseAutoConfiguration.class)
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.0-alpine");

    @Autowired UserRepository userRepository;
    @Autowired TestEntityManager entityManager;

    private User alice;
    private User bob;
    private User charlie;

    @BeforeEach
    void setUp() {
        alice = new User();
        alice.setUsername("alice");
        alice.setPassword("$2a$pass");
        alice = userRepository.save(alice);

        bob = new User();
        bob.setUsername("bob");
        bob.setPassword("$2a$pass");
        bob = userRepository.save(bob);

        charlie = new User();
        charlie.setUsername("charlie");
        charlie.setPassword("$2a$pass");
        charlie = userRepository.save(charlie);

        entityManager.flush();
    }

    // ======================== FIND BY USERNAME ========================

    @Test
    void findByUsername_existingUser_returnsUser() {
        Optional<User> result = userRepository.findByUsername("alice");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("alice");
    }

    @Test
    void findByUsername_nonExistingUser_returnsEmpty() {
        Optional<User> result = userRepository.findByUsername("nobody");

        assertThat(result).isEmpty();
    }

    // ======================== EXISTS BY ID ========================

    @Test
    void existsById_existingUser_returnsTrue() {
        assertThat(userRepository.existsById(alice.getId())).isTrue();
    }

    @Test
    void existsById_nonExistingUser_returnsFalse() {
        assertThat(userRepository.existsById(999999L)).isFalse();
    }

    // ======================== FIND USER DTO BY ID ========================

    @Test
    void findUserDtoById_existingUser_returnsDto() {
        Optional<UserDto> result = userRepository.findUserDtoById(alice.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("alice");
        assertThat(result.get().getId()).isEqualTo(alice.getId());
    }

    @Test
    void findUserDtoById_nonExistingUser_returnsEmpty() {
        Optional<UserDto> result = userRepository.findUserDtoById(999999L);

        assertThat(result).isEmpty();
    }

    // ======================== FIND USER DTO BY IDS ========================

    @Test
    void findUserDtoByIds_multipleIds_returnsList() {
        List<UserDto> result = userRepository.findUserDtoByIds(List.of(alice.getId(), bob.getId()));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserDto::getUsername)
                .containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void findUserDtoByIds_singleId_returnsSingleItem() {
        List<UserDto> result = userRepository.findUserDtoByIds(List.of(charlie.getId()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("charlie");
    }

    @Test
    void findUserDtoByIds_withPagination_returnsPage() {
        List<Long> ids = List.of(alice.getId(), bob.getId(), charlie.getId());
        Page<UserDto> result = userRepository.findUserDtoByIds(ids, PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    // ======================== FIND BY USERNAME STARTING WITH (CASE INSENSITIVE) ========================

    @Test
    void findByUsernameStartingWithIgnoreCase_caseInsensitive_returnsMatches() {
        Page<User> result = userRepository.findByUsernameStartingWithIgnoreCase("ALI", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("alice");
    }

    @Test
    void findByUsernameStartingWithIgnoreCase_noMatches_returnsEmpty() {
        Page<User> result = userRepository.findByUsernameStartingWithIgnoreCase("xyz", PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    // ======================== FIND BY USERNAME WITH FRIEND PRIORITY ========================

    @Test
    void findByUsernameStartingWithOrderByFriendPriority_friendsFirst() {
        // Search with bob as a "friend" (his ID is in friendIds)
        List<Long> friendIds = List.of(bob.getId());
        Page<User> result = userRepository.findByUsernameStartingWithOrderByFriendPriority(
                "b", friendIds, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("bob");
    }

    @Test
    void findByUsernameStartingWithOrderByFriendPriority_emptyFriendIds_returnsAll() {
        Page<User> result = userRepository.findByUsernameStartingWithOrderByFriendPriority(
                "a", List.of(), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("alice");
    }

    @Test
    void findByUsernameStartingWithOrderByFriendPriority_multipleUsers_friendsRankedFirst() {
        // Create additional user "bob2" to test ordering
        User bob2 = new User();
        bob2.setUsername("bob2");
        bob2.setPassword("$2a$pass");
        bob2 = userRepository.save(bob2);
        entityManager.flush();

        // bob is a friend, bob2 is not
        List<Long> friendIds = List.of(bob.getId());
        Page<User> result = userRepository.findByUsernameStartingWithOrderByFriendPriority(
                "bob", friendIds, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        // First result should be "bob" (the friend)
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("bob");
        assertThat(result.getContent().get(1).getUsername()).isEqualTo("bob2");
    }

    // ======================== FIND USER DTO BY USERNAME STARTING WITH ========================

    @Test
    void findUserDtoByUsernameStartingWith_returnsMatchingDtos() {
        List<UserDto> result = userRepository.findUserDtoByUsernameStartingWith("ali");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("alice");
    }
}
