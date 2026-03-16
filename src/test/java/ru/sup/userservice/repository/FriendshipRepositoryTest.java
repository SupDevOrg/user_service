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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.sup.userservice.data.FriendshipStatus;
import ru.sup.userservice.entity.Friendship;
import ru.sup.userservice.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(LiquibaseAutoConfiguration.class)
class FriendshipRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.0-alpine");

    @Autowired FriendshipRepository friendshipRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestEntityManager entityManager;

    private User alice;
    private User bob;
    private User charlie;

    @BeforeEach
    void setUp() {
        alice = new User();
        alice.setUsername("alice_fr");
        alice.setPassword("$2a$pass");
        alice = userRepository.save(alice);

        bob = new User();
        bob.setUsername("bob_fr");
        bob.setPassword("$2a$pass");
        bob = userRepository.save(bob);

        charlie = new User();
        charlie.setUsername("charlie_fr");
        charlie.setPassword("$2a$pass");
        charlie = userRepository.save(charlie);

        entityManager.flush();
    }

    private Friendship saveFriendship(User requester, User addressee, FriendshipStatus status) {
        Friendship f = Friendship.builder()
                .requester(requester)
                .addressee(addressee)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
        Friendship saved = friendshipRepository.save(f);
        entityManager.flush();
        return saved;
    }

    // ======================== FIND BY REQUESTER AND ADDRESSEE ========================

    @Test
    void findByRequesterIdAndAddresseeId_exists_returnsOptional() {
        saveFriendship(alice, bob, FriendshipStatus.PENDING);

        Optional<Friendship> result = friendshipRepository
                .findByRequesterIdAndAddresseeId(alice.getId(), bob.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(FriendshipStatus.PENDING);
    }

    @Test
    void findByRequesterIdAndAddresseeId_notExists_returnsEmpty() {
        Optional<Friendship> result = friendshipRepository
                .findByRequesterIdAndAddresseeId(alice.getId(), bob.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByRequesterIdAndAddresseeId_reverseDirection_returnsEmpty() {
        saveFriendship(alice, bob, FriendshipStatus.PENDING);

        // Reversed direction should return empty (directional query)
        Optional<Friendship> result = friendshipRepository
                .findByRequesterIdAndAddresseeId(bob.getId(), alice.getId());

        assertThat(result).isEmpty();
    }

    // ======================== FIND BY ADDRESSEE + STATUS ========================

    @Test
    void findByAddresseeIdAndStatus_pendingRequests_returnsList() {
        saveFriendship(alice, bob, FriendshipStatus.PENDING);
        saveFriendship(charlie, bob, FriendshipStatus.PENDING);

        List<Friendship> result = friendshipRepository
                .findByAddresseeIdAndStatus(bob.getId(), FriendshipStatus.PENDING);

        assertThat(result).hasSize(2);
    }

    @Test
    void findByAddresseeIdAndStatus_wrongStatus_returnsEmpty() {
        saveFriendship(alice, bob, FriendshipStatus.ACCEPTED);

        List<Friendship> result = friendshipRepository
                .findByAddresseeIdAndStatus(bob.getId(), FriendshipStatus.PENDING);

        assertThat(result).isEmpty();
    }

    // ======================== FIND BY REQUESTER + STATUS ========================

    @Test
    void findByRequesterIdAndStatus_outgoingRequests_returnsList() {
        saveFriendship(alice, bob, FriendshipStatus.PENDING);
        saveFriendship(alice, charlie, FriendshipStatus.PENDING);

        List<Friendship> result = friendshipRepository
                .findByRequesterIdAndStatus(alice.getId(), FriendshipStatus.PENDING);

        assertThat(result).hasSize(2);
    }

    // ======================== ARE FRIENDS ========================

    @Test
    void areFriends_acceptedFriendship_returnsTrue() {
        saveFriendship(alice, bob, FriendshipStatus.ACCEPTED);

        assertThat(friendshipRepository.areFriends(alice.getId(), bob.getId())).isTrue();
    }

    @Test
    void areFriends_acceptedFriendship_bidirectional_returnsTrue() {
        saveFriendship(alice, bob, FriendshipStatus.ACCEPTED);

        // Should return true in reverse direction too
        assertThat(friendshipRepository.areFriends(bob.getId(), alice.getId())).isTrue();
    }

    @Test
    void areFriends_pendingStatus_returnsFalse() {
        saveFriendship(alice, bob, FriendshipStatus.PENDING);

        assertThat(friendshipRepository.areFriends(alice.getId(), bob.getId())).isFalse();
    }

    @Test
    void areFriends_noRelationship_returnsFalse() {
        assertThat(friendshipRepository.areFriends(alice.getId(), charlie.getId())).isFalse();
    }

    // ======================== COUNT ACCEPTED FRIENDS ========================

    @Test
    void countAcceptedFriends_withFriends_returnsCorrectCount() {
        saveFriendship(alice, bob, FriendshipStatus.ACCEPTED);
        saveFriendship(alice, charlie, FriendshipStatus.ACCEPTED);

        long count = friendshipRepository.countAcceptedFriends(alice.getId());

        assertThat(count).isEqualTo(2);
    }

    @Test
    void countAcceptedFriends_friendInEitherDirection_counted() {
        // bob sent request to alice, alice is addressee
        saveFriendship(bob, alice, FriendshipStatus.ACCEPTED);

        long count = friendshipRepository.countAcceptedFriends(alice.getId());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void countAcceptedFriends_noFriends_returnsZero() {
        long count = friendshipRepository.countAcceptedFriends(alice.getId());

        assertThat(count).isEqualTo(0);
    }

    // ======================== FIND ACCEPTED FRIEND IDS ========================

    @Test
    void findAcceptedFriendIds_returnsOtherUserIds() {
        saveFriendship(alice, bob, FriendshipStatus.ACCEPTED);
        saveFriendship(alice, charlie, FriendshipStatus.ACCEPTED);

        List<Long> friendIds = friendshipRepository.findAcceptedFriendIds(alice.getId());

        assertThat(friendIds).containsExactlyInAnyOrder(bob.getId(), charlie.getId());
    }

    @Test
    void findAcceptedFriendIds_asAddressee_returnsRequesterId() {
        saveFriendship(bob, alice, FriendshipStatus.ACCEPTED); // bob sent to alice

        List<Long> friendIds = friendshipRepository.findAcceptedFriendIds(alice.getId());

        assertThat(friendIds).containsExactly(bob.getId()); // should return bob's ID
    }

    @Test
    void findAcceptedFriendIds_noFriends_returnsEmpty() {
        List<Long> friendIds = friendshipRepository.findAcceptedFriendIds(alice.getId());

        assertThat(friendIds).isEmpty();
    }

    @Test
    void findAcceptedFriendIds_pendingNotIncluded() {
        saveFriendship(alice, bob, FriendshipStatus.PENDING);

        List<Long> friendIds = friendshipRepository.findAcceptedFriendIds(alice.getId());

        assertThat(friendIds).isEmpty();
    }

    // ======================== FIND BY USER PAIR ========================

    @Test
    void findByUserPair_aliceRequester_returnsResult() {
        Friendship f = saveFriendship(alice, bob, FriendshipStatus.PENDING);

        Optional<Friendship> result = friendshipRepository.findByUserPair(alice.getId(), bob.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(f.getId());
    }

    @Test
    void findByUserPair_aliceAddressee_returnsResult() {
        Friendship f = saveFriendship(bob, alice, FriendshipStatus.PENDING); // bob is requester

        // Should still find it when alice is searched as user1
        Optional<Friendship> result = friendshipRepository.findByUserPair(alice.getId(), bob.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(f.getId());
    }

    @Test
    void findByUserPair_noRelationship_returnsEmpty() {
        Optional<Friendship> result = friendshipRepository.findByUserPair(alice.getId(), charlie.getId());

        assertThat(result).isEmpty();
    }

    // ======================== FIND BY USER PAIR AND STATUS ACCEPTED ========================

    @Test
    void findByUserPairAndStatusAccepted_accepted_returnsResult() {
        Friendship f = saveFriendship(alice, bob, FriendshipStatus.ACCEPTED);

        Optional<Friendship> result = friendshipRepository.findByUserPairAndStatusAccepted(alice.getId(), bob.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
    }

    @Test
    void findByUserPairAndStatusAccepted_notAccepted_returnsEmpty() {
        saveFriendship(alice, bob, FriendshipStatus.PENDING);

        Optional<Friendship> result = friendshipRepository.findByUserPairAndStatusAccepted(alice.getId(), bob.getId());

        assertThat(result).isEmpty();
    }
}
