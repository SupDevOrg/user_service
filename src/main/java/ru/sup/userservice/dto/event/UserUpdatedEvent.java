package ru.sup.userservice.dto.event;

public record UserUpdatedEvent(
        Long userId,
        String field,
        String oldValue,
        String newValue,
        long timestamp
) {
    public UserUpdatedEvent(Long userId, String field, String oldValue, String newValue) {
        this(userId, field, oldValue, newValue, System.currentTimeMillis());
    }
}
