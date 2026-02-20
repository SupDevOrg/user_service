package ru.sup.userservice.dto;

import ru.sup.userservice.entity.Friendship;
import ru.sup.userservice.data.FriendshipStatus;
import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record FriendshipDto(
        Long id,
        Long requesterId,
        Long addresseeId,
        FriendshipStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FriendshipDto from(Friendship friendship) {
        return FriendshipDto.builder()
                .id(friendship.getId())
                .requesterId(friendship.getRequester().getId())
                .addresseeId(friendship.getAddressee().getId())
                .status(friendship.getStatus())
                .createdAt(friendship.getCreatedAt())
                .updatedAt(friendship.getUpdatedAt())
                .build();
    }
}