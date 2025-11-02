package ru.sup.userservice.dto;

public record UserCreatedEvent(
        Long userId,
        String username,
        long timestamp
) {
    public UserCreatedEvent(Long userId, String username) {
        this(userId, username, System.currentTimeMillis());
    }
}