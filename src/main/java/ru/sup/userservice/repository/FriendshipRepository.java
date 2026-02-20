package ru.sup.userservice.repository;

import ru.sup.userservice.entity.Friendship;
import ru.sup.userservice.data.FriendshipStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    Optional<Friendship> findByRequesterIdAndAddresseeId(Long requesterId, Long addresseeId);

    @Query("""
        SELECT f FROM Friendship f 
        WHERE f.addressee.id = :userId AND f.status = :status
        ORDER BY f.createdAt DESC
        """)
    List<Friendship> findByAddresseeIdAndStatus(@Param("userId") Long userId,
                                                @Param("status") FriendshipStatus status);

    @Query("""
        SELECT f FROM Friendship f 
        WHERE f.requester.id = :userId AND f.status = :status
        ORDER BY f.createdAt DESC
        """)
    List<Friendship> findByRequesterIdAndStatus(@Param("userId") Long userId,
                                                @Param("status") FriendshipStatus status);

    @Query("""
        SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f 
        WHERE ((f.requester.id = :user1 AND f.addressee.id = :user2) OR 
               (f.requester.id = :user2 AND f.addressee.id = :user1)) 
        AND f.status = 'ACCEPTED'
        """)
    boolean areFriends(@Param("user1") Long user1, @Param("user2") Long user2);

    @Query("""
        SELECT COUNT(f) FROM Friendship f 
        WHERE (f.requester.id = :userId OR f.addressee.id = :userId) 
        AND f.status = 'ACCEPTED'
        """)
    long countAcceptedFriends(@Param("userId") Long userId);

    @Query("""
        SELECT CASE 
            WHEN f.requester.id = :user1 THEN f.addressee.id 
            ELSE f.requester.id 
        END FROM Friendship f 
        WHERE (f.requester.id = :user1 OR f.addressee.id = :user1) 
        AND f.status = 'ACCEPTED'
        """)
    List<Long> findAcceptedFriendIds(@Param("user1") Long userId);

    @Query("""
        SELECT CASE 
            WHEN f.requester.id = :user1 THEN f.addressee.id 
            ELSE f.requester.id 
        END FROM Friendship f 
        WHERE (f.requester.id = :user1 OR f.addressee.id = :user1) 
        AND f.status = 'ACCEPTED'
        """)
    List<Long> findAcceptedFriendIdsPaged(@Param("user1") Long userId, Pageable pageable);

    @Query("""
        SELECT f FROM Friendship f 
        WHERE (f.requester.id = :user1 AND f.addressee.id = :user2) OR 
              (f.requester.id = :user2 AND f.addressee.id = :user1)
        """)
    Optional<Friendship> findByUserPair(@Param("user1") Long user1, @Param("user2") Long user2);

    @Query("""
        SELECT f FROM Friendship f 
        WHERE ((f.requester.id = :user1 AND f.addressee.id = :user2) OR 
               (f.requester.id = :user2 AND f.addressee.id = :user1)) 
        AND f.status = 'ACCEPTED'
        """)
    Optional<Friendship> findByUserPairAndStatusAccepted(@Param("user1") Long user1, @Param("user2") Long user2);
}