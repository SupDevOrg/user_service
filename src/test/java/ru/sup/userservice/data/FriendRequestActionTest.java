package ru.sup.userservice.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FriendRequestActionTest {

    @Test
    void enumContainsAllExpectedActions() {
        assertEquals(FriendRequestAction.REQUEST_SENT, FriendRequestAction.valueOf("REQUEST_SENT"));
        assertEquals(FriendRequestAction.REQUEST_ACCEPTED, FriendRequestAction.valueOf("REQUEST_ACCEPTED"));
        assertEquals(FriendRequestAction.REQUEST_REJECTED, FriendRequestAction.valueOf("REQUEST_REJECTED"));
        assertEquals(FriendRequestAction.REQUEST_CANCELLED, FriendRequestAction.valueOf("REQUEST_CANCELLED"));
        assertEquals(FriendRequestAction.FRIEND_REMOVED, FriendRequestAction.valueOf("FRIEND_REMOVED"));
        assertEquals(FriendRequestAction.USER_BLOCKED, FriendRequestAction.valueOf("USER_BLOCKED"));
    }
}
