package ru.sup.userservice.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record FriendshipStatusChangedEvent(
        @JsonProperty("requesterId") Long requesterId,
        @JsonProperty("addresseeId") Long addresseeId,
        @JsonProperty("oldStatus") String oldStatus,
        @JsonProperty("newStatus") String newStatus,
        @JsonProperty("timestamp") LocalDateTime timestamp
) {
    public FriendshipStatusChangedEvent {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
