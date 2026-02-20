package ru.sup.userservice.dto;

import ru.sup.userservice.data.FriendshipStatus;

public record FriendshipStatusDto(
        FriendshipStatus status,
        boolean isOutgoingRequest
) {}