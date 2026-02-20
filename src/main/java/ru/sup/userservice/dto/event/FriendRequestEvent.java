package ru.sup.userservice.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import ru.sup.userservice.data.FriendRequestAction;

import java.time.LocalDateTime;

@Builder
public record FriendRequestEvent(
        @JsonProperty("recipientId") Long recipientId,
        @JsonProperty("senderId") Long senderId,
        @JsonProperty("action") FriendRequestAction action,
        @JsonProperty("timestamp") LocalDateTime timestamp
) {
    public FriendRequestEvent {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
