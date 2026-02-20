package ru.sup.userservice.entity;

import jakarta.persistence.*;
import lombok.*;
import ru.sup.userservice.data.FriendshipStatus;

import java.time.LocalDateTime;


@Table(name = "friendships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"requester_id", "addressee_id"}),
        indexes = {
                @Index(name = "idx_friendships_requester_status", columnList = "requester_id, status"),
                @Index(name = "idx_friendships_addressee_status", columnList = "addressee_id, status"),
                @Index(name = "idx_friendships_created", columnList = "created_at")
        })
@Getter
@Setter
@Entity
@Builder
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@AllArgsConstructor
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "addressee_id", nullable = false)
    private User addressee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendshipStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}