package ru.sup.userservice.dto.event;

import org.junit.jupiter.api.Test;
import ru.sup.userservice.data.FriendRequestAction;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EventDtoTest {

    @Test
    void userCreatedAndUpdated_eventsSetTimestamp() {
        UserCreatedEvent created = new UserCreatedEvent(1L, "alice");
        UserUpdatedEvent updated = new UserUpdatedEvent(1L, "username", "old", "new");

        assertEquals(1L, created.userId());
        assertEquals("alice", created.username());
        assertNotNull(created.timestamp());

        assertEquals(1L, updated.userId());
        assertEquals("username", updated.field());
        assertNotNull(updated.timestamp());
    }

    @Test
    void friendshipEvents_setNowWhenTimestampNull() {
        FriendRequestEvent req = new FriendRequestEvent(2L, 1L, FriendRequestAction.REQUEST_SENT, null);
        FriendshipStatusChangedEvent status = new FriendshipStatusChangedEvent(1L, 2L, "PENDING", "ACCEPTED", null);

        assertNotNull(req.timestamp());
        assertNotNull(status.timestamp());
    }

    @Test
    void friendshipEvents_keepProvidedTimestamp() {
        LocalDateTime now = LocalDateTime.now().minusMinutes(1);
        FriendRequestEvent req = new FriendRequestEvent(2L, 1L, FriendRequestAction.REQUEST_ACCEPTED, now);
        FriendshipStatusChangedEvent status = new FriendshipStatusChangedEvent(1L, 2L, "PENDING", "ACCEPTED", now);

        assertEquals(now, req.timestamp());
        assertEquals(now, status.timestamp());
    }
}
